package com.example.productcatalogservice.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import com.example.productcatalogservice.exception.SearchUnavailableException;
import com.example.productcatalogservice.model.Product;
import com.example.productcatalogservice.model.ProductSearchDoc;
import com.example.productcatalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tìm kiếm qua Elasticsearch — chứa doc search-only của product ACTIVE.
 * Mongo vẫn là write source-of-truth; service này chỉ phục vụ search rồi trả về page product-id
 * để caller hydrate + reorder từ Mongo. ES down → ném {@link SearchUnavailableException} để caller fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService implements ApplicationRunner {

    /** ES trả tối đa 10000 hit (max_result_window mặc định) — giới hạn cho unpaged. */
    private static final int MAX_WINDOW = 10000;

    private final ElasticsearchOperations operations;
    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureIndexAndBootstrap();
        } catch (Exception e) {
            // ES chưa lên lúc boot không được làm crash app — lần search đầu sẽ self-heal.
            log.warn("ES bootstrap skipped: {}", e.toString());
        }
    }

    /** Tạo index + mapping (brand/categoryId Keyword) nếu chưa có, rồi index toàn bộ product ACTIVE. */
    public void ensureIndexAndBootstrap() {
        IndexOperations indexOps = operations.indexOps(ProductSearchDoc.class);
        if (!indexOps.exists()) {
            indexOps.create();
        }
        // putMapping merge — gọi lại mỗi lần để đảm bảo brand/categoryId là Keyword (idempotent)
        indexOps.putMapping(indexOps.createMapping(ProductSearchDoc.class));

        List<Product> active = productRepository.findAllByIsActiveTrue(Pageable.unpaged()).getContent();
        if (!active.isEmpty()) {
            active.forEach(this::index);
            log.info("reindexed {}", active.size());
        }
        // ponytail: quét toàn bộ product ACTIVE mỗi lần boot — đủ cho dữ liệu demo; khi lớn thì
        // thay bằng đánh dấu updatedAt > lastReindex hoặc outbox/CDC.
    }

    /** Index (upsert) một product vào ES. Lỗi kết nối → bỏ qua; lỗi khác (schema/DSL) → throw. */
    public void index(Product product) {
        try {
            operations.save(ProductSearchDoc.from(product));
        } catch (RuntimeException e) {
            if (isConnectivity(e)) {
                log.warn("ES index skip (unavailable), product {}", product.getId());
            } else {
                throw e;
            }
        }
    }

    /** Xóa doc khỏi ES khi product soft-delete. Lỗi kết nối → bỏ qua; lỗi khác → throw. */
    public void remove(String id) {
        try {
            operations.delete(id, ProductSearchDoc.class);
        } catch (RuntimeException e) {
            if (isConnectivity(e)) {
                log.warn("ES remove skip (unavailable), id {}", id);
            } else {
                throw e;
            }
        }
    }

    /** Search theo keyword/categoryIds/brand, trả page product-id. Tự heal khi index chưa tồn tại. */
    public Page<String> search(String keyword, List<String> categoryIds, String brand, Pageable pageable) {
        return doSearch(keyword, categoryIds, brand, pageable, true);
    }

    private Page<String> doSearch(String keyword, List<String> categoryIds, String brand, Pageable pageable, boolean allowHeal) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String b = (brand == null || brand.isBlank()) ? null : brand.trim();
        List<String> catIds = (categoryIds == null || categoryIds.isEmpty()) ? null : List.copyOf(categoryIds);

        try {
            List<Query> musts = new ArrayList<>();
            if (kw != null) {
                // Keyword search: relevance mặc định (_score) — không sort createdAt
                musts.add(Query.of(q -> q.multiMatch(m -> m
                        .query(kw)
                        .fields("name^3", "slug^2", "description"))));
            } else {
                // Browse: matchAll làm nền, lọc bằng filter, sort createdAt desc
                musts.add(Query.of(q -> q.matchAll(m -> m)));
            }

            List<Query> filters = new ArrayList<>();
            if (catIds != null) {
                filters.add(Query.of(q -> q.terms(t -> t.field("categoryId")
                        .terms(TermsQueryField.of(tf -> tf.value(catIds.stream().map(FieldValue::of).toList()))))));
            }
            if (b != null) {
                filters.add(Query.of(q -> q.term(t -> t.field("brand").value(b))));
            }

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            boolBuilder.must(musts);
            if (!filters.isEmpty()) {
                boolBuilder.filter(filters);
            }
            Query query = new Query.Builder().bool(boolBuilder.build()).build();

            // Bỏ sort trong pageable khi gửi xuống ES: keyword dùng _score, browse sort tay createdAt desc.
            // Tránh sort mặc định của controller (createdAt desc) nuốt relevance của keyword.
            Pageable esPageable = pageable.isUnpaged()
                    ? PageRequest.of(0, MAX_WINDOW)
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

            NativeQueryBuilder nativeQueryBuilder = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(esPageable);
            if (kw == null) {
                nativeQueryBuilder.withSort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)));
            }

            SearchHits<ProductSearchDoc> hits = operations.search(nativeQueryBuilder.build(), ProductSearchDoc.class);
            List<String> ids = hits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getId())
                    .toList();
            return new PageImpl<>(ids, pageable, hits.getTotalHits());
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                if (allowHeal) {
                    try {
                        ensureIndexAndBootstrap();
                    } catch (Exception healFailure) {
                        throw new SearchUnavailableException("ES unavailable during bootstrap: " + healFailure.getMessage(), healFailure);
                    }
                    return doSearch(keyword, catIds, b, pageable, false);
                }
                throw e;
            }
            if (isConnectivity(e)) {
                throw new SearchUnavailableException("Elasticsearch unavailable: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /** Đi dọc cause-chain tìm lỗi kết nối (ES down / restart / DNS). */
    private boolean isConnectivity(Throwable t) {
        while (t != null) {
            if (t instanceof IOException
                    || t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof SocketTimeoutException
                    || t instanceof NoRouteToHostException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** Đi dọc cause-chain tìm index_not_found_exception (ES 404 — index chưa tồn tại). */
    private boolean isIndexNotFound(Throwable t) {
        while (t != null) {
            if (t instanceof co.elastic.clients.elasticsearch._types.ElasticsearchException ee
                    && ee.status() == 404
                    && ee.error() != null
                    && "index_not_found_exception".equals(ee.error().type())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
