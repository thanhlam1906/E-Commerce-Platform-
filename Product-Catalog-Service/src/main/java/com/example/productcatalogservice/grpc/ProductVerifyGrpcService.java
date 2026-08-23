package com.example.productcatalogservice.grpc;

import com.example.productcatalogservice.model.Product;
import com.example.productcatalogservice.repository.ProductRepository;
import com.voltstack.ecommerce.grpc.ProductSnapshot;
import com.voltstack.ecommerce.grpc.ProductVerifyServiceGrpc;
import com.voltstack.ecommerce.grpc.SkuRequest;
import com.voltstack.ecommerce.grpc.VerifySkuResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
public class ProductVerifyGrpcService extends ProductVerifyServiceGrpc.ProductVerifyServiceImplBase {

    private final ProductRepository productRepository;

    @Override
    public void verifySku(SkuRequest request, StreamObserver<VerifySkuResponse> responseObserver) {
        Optional<ProductSnapshot> snapshot = findSnapshot(request.getSku());
        VerifySkuResponse response = VerifySkuResponse.newBuilder()
                .setExists(snapshot.isPresent())
                .setSnapshot(snapshot.orElse(ProductSnapshot.getDefaultInstance()))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getProductSnapshot(SkuRequest request, StreamObserver<ProductSnapshot> responseObserver) {
        Optional<ProductSnapshot> snapshot = findSnapshot(request.getSku());
        if (snapshot.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Product snapshot not found for sku: " + request.getSku())
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(snapshot.get());
        responseObserver.onCompleted();
    }

    private Optional<ProductSnapshot> findSnapshot(String sku) {
        return productRepository.findByVariantsSku(sku)
                .filter(Product::isActive)
                .flatMap(product -> product.getVariants().stream()
                        .filter(variant -> variant.getSku().equals(sku))
                        .findFirst()
                        .map(variant -> ProductSnapshot.newBuilder()
                                .setSku(variant.getSku())
                                .setProductName(product.getName())
                                .setVariantName(variant.getName())
                                .setPrice(variant.getPrice().toPlainString())
                                .build()));
    }
}
