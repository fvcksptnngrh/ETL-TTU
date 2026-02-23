package com.ttu.etl.service;

import com.ttu.etl.dto.ProductDto;
import com.ttu.etl.dto.ProductDto.Variation;
import com.ttu.etl.entity.Product;
import com.ttu.etl.repository.ProductRepository;
import com.ttu.etl.repository.TransactionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final TransactionItemRepository transactionItemRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> getProducts(boolean onlyInconsistencies) {
        // Build variation map from transaction_items (1 query, no extra table)
        Map<String, List<Variation>> variationMap = buildVariationMap();

        List<Product> products = productRepository.findAllWithEtlJob();
        List<ProductDto> result = new ArrayList<>();

        for (Product p : products) {
            List<Variation> variations = variationMap.getOrDefault(p.getItemCode(), List.of());
            boolean hasVariations = variations.size() > 1;

            if (onlyInconsistencies && !hasVariations) {
                continue;
            }

            result.add(ProductDto.builder()
                    .itemCode(p.getItemCode())
                    .itemName(p.getItemName())
                    .defaultUnit(p.getDefaultUnit())
                    .firstSeenInFile(p.getEtlJob() != null ? p.getEtlJob().getFileName() : null)
                    .hasVariations(hasVariations)
                    .variations(hasVariations ? variations : null)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<ProductDto> getProduct(String itemCode) {
        Map<String, List<Variation>> variationMap = buildVariationMap();

        return productRepository.findByItemCodeWithEtlJob(itemCode).map(p -> {
            List<Variation> variations = variationMap.getOrDefault(p.getItemCode(), List.of());
            return ProductDto.builder()
                    .itemCode(p.getItemCode())
                    .itemName(p.getItemName())
                    .defaultUnit(p.getDefaultUnit())
                    .firstSeenInFile(p.getEtlJob() != null ? p.getEtlJob().getFileName() : null)
                    .hasVariations(variations.size() > 1)
                    .variations(variations.isEmpty() ? null : variations)
                    .build();
        });
    }

    /**
     * Query all distinct (itemCode, itemName, unit, fileName) combinations
     * from transaction_items in 1 query. No extra table needed.
     */
    private Map<String, List<Variation>> buildVariationMap() {
        List<Object[]> rows = transactionItemRepository.findDistinctProductVariations();
        Map<String, List<Variation>> map = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String itemCode = (String) row[0];
            String itemName = (String) row[1];
            String unit = (String) row[2];
            String fileName = (String) row[3];

            map.computeIfAbsent(itemCode, k -> new ArrayList<>())
                    .add(Variation.builder()
                            .itemName(itemName)
                            .unit(unit)
                            .fileName(fileName)
                            .build());
        }
        return map;
    }
}
