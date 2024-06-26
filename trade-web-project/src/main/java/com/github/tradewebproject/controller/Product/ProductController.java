package com.github.tradewebproject.controller.Product;


import com.github.tradewebproject.Dto.Product.DetailProductDto;
import com.github.tradewebproject.Dto.Product.ProductDTO;
import com.github.tradewebproject.Dto.Product.ProductPageResponseDto;
import com.github.tradewebproject.repository.User.UserRepository;
import com.github.tradewebproject.service.Product.ProductService;
import com.github.tradewebproject.util.FileStorageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.Date;
import java.util.List;

@Tag(name = "Product Controller", description = "상품 관련 API")
@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private UserRepository userRepository;


    @GetMapping("/products/user/{userId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "등록된 상품조회", description = "사용자가 등록한 상품 목록을 조회합니다. 페이지와 정렬 방식에 따라 조회할 수 있습니다.")
    public ProductPageResponseDto getProductsByUserId(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 당 상품 수") @RequestParam(defaultValue = "8") int size,
            @Parameter(description = "정렬 방식 (asc: 오름차순, desc: 내림차순, enddate: 종료 날짜 순)") @RequestParam(defaultValue = "enddate") String sort) {
        return productService.getProductsByUserId(userId, page, size, sort);
    }


    @PostMapping("/products/register")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다.")
    public ResponseEntity<?> registerProduct(
            @RequestParam("productName") String productName,
            @RequestParam("price") int price,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam("description") String description,
            @RequestParam("productQuality") String productQuality,
            @RequestParam("category") String category,
            @RequestParam("files") List<MultipartFile> files,
            Principal principal) {
        try {
            String userEmail = principal.getName();

            ProductDTO productDTO = new ProductDTO();
            productDTO.setProductName(productName);
            productDTO.setPrice(price);
            productDTO.setStartDate(startDate);
            productDTO.setEndDate(endDate);
            productDTO.setDescription(description);
            productDTO.setProductQuality(productQuality);
            productDTO.setCategory(category);
            productDTO.setFiles(files);

            ProductDTO registeredProduct = productService.registerProduct(userEmail, productDTO);

            return ResponseEntity.ok(registeredProduct);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("파일 저장 중 오류가 발생했습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("상품 등록 중 오류가 발생했습니다.");
        }
    }



    @PutMapping("/products/{productId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "상품 정보 수정", description = "상품 ID에 해당하는 상품의 정보를 수정합니다.")
    public ResponseEntity<?> updateProduct(
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("productName") String productName,
            @RequestParam("price") int price,
            //@RequestParam("stock") int stock,
            //@RequestParam("productOption") String productOption,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam("description") String description,
            @RequestParam("productQuality") String productQuality,
            @RequestParam("category") String category,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            ProductDTO productDTO = new ProductDTO();
            productDTO.setProductName(productName);
            productDTO.setPrice(price);
            //productDTO.setStock(stock);
            //productDTO.setProductOption(productOption);
            productDTO.setStartDate(startDate);
            productDTO.setEndDate(endDate);
            productDTO.setDescription(description);
            productDTO.setProductQuality(productQuality);
            productDTO.setCategory(category);
            productDTO.setFiles(files);

            ProductDTO updatedProduct = productService.updateProduct(productId, email, productDTO, password);
            return ResponseEntity.ok(updatedProduct);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("파일 저장 중 오류가 발생했습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("상품 정보 업데이트 중 오류가 발생했습니다.");
        }
    }


    @GetMapping("/product")
    @Operation(summary = "전체 상품조회", description = "전체 상품을 조회합니다.")
    public ProductPageResponseDto getAllProducts(
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 당 상품 수") @RequestParam(defaultValue = "8") int size,
            @Parameter(description = "정렬 방식 (asc: 오름차순, desc: 내림차순, enddate: 종료일 오름차순)") @RequestParam(defaultValue = "endDate") String sort) {

        return productService.getAllProducts(page, size, sort);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "상품 상세 조회", description = "상품 ID에 해당하는 상품의 상세 정보를 조회합니다.")
    public DetailProductDto getProductById(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        return productService.getProductById(productId);
    }

    @GetMapping("/product/search")
    @Operation(summary = "상품 검색", description = "상품 키워드(제목)에 맞는 상품을 조회합니다.")
    public ResponseEntity<ProductPageResponseDto> searchProducts(
            @Parameter(description = "입력 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 당 상품 수") @RequestParam(defaultValue = "8") int size,
            @Parameter(description = "정렬 방식 (asc: 오름차순, desc: 내림차순, enddate: 종료일 오름차순") @RequestParam(defaultValue = "asc") String sort,
            @Parameter(description = "최소 가격") @RequestParam(required = false) Integer minPrice,
            @Parameter(description = "최대 가격") @RequestParam(required = false) Integer maxPrice,
            @Parameter(description = "카테고리") @RequestParam(required = false) String category,
            @Parameter(description = "상품 상태(새상품, 중고상품)") @RequestParam(required = false) String quality) {
        ProductPageResponseDto responseDto = productService.searchProducts(keyword, page, size, sort, minPrice, maxPrice, category, quality);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/products/interests")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "관심사에 맞는 상품 조회", description = "사용자의 관심사에 맞는 카테고리의 상품을 조회합니다.")
    public ResponseEntity<ProductPageResponseDto> getProductsByUserInterests(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "asc") String sort) {
        ProductPageResponseDto productPage = productService.getProductsByUserInterests(principal.getName(), page, size, sort);
        return ResponseEntity.ok(productPage);
    }



}