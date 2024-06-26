package com.github.tradewebproject.service.Review;

import com.github.tradewebproject.Dto.Review.ReviewCreationResponseDto;
import com.github.tradewebproject.Dto.Review.ReviewResponseDto;
import com.github.tradewebproject.Dto.Review.SellerReviewPageDto;
import com.github.tradewebproject.Dto.Review.SellerReviewResponseDto;
import com.github.tradewebproject.domain.Product;
import com.github.tradewebproject.domain.Purchase;
import com.github.tradewebproject.domain.Review;
import com.github.tradewebproject.domain.User;
import com.github.tradewebproject.repository.Product.ProductRepository;
import com.github.tradewebproject.repository.Purchase.PurchaseRepository;
import com.github.tradewebproject.repository.Review.ReviewRepository;
import com.github.tradewebproject.repository.User.UserJpaRepository;
import com.github.tradewebproject.repository.User.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private UserJpaRepository userJpaRepository;

    @Transactional
    public ReviewResponseDto createReview(String email, Long productId, String reviewContent, Double rating, String reviewTitle) {
        User user = userRepository.findByEmail2(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // 판매자 ID 가져오기
        Long sellerId = product.getUser().getUserId();

        Purchase purchase = purchaseRepository.findByUserAndProduct(user, product)
                .orElseThrow(() -> new RuntimeException("해당 상품에 대한 구매 내역이 없습니다."));

        Review review = new Review();
        review.setPurchase(purchase);
        review.setProduct(product);
        review.setReviewerNickname(user.getUserNickname());
        review.setRating(rating);
        review.setReviewContent(reviewContent);
        review.setReviewDate(new Date());
        review.setReviewTitle(reviewTitle);
        review.setUser(user);
        review.setSellerId(sellerId); // sellerId 설정
        review = reviewRepository.save(review);

        return new ReviewResponseDto(reviewTitle, reviewContent, productId, user.getUserId(), rating, review.getReviewId(), sellerId);
    }

    @Transactional
    public List<ReviewResponseDto> getReviewsByUserId(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Review> reviews = reviewRepository.findByUser(user);
        return reviews.stream()
                .map(review -> new ReviewResponseDto(
                        review.getReviewTitle(),
                        review.getReviewContent(),
                        review.getProduct().getProductId(),
                        review.getUser().getUserId(),
                        review.getRating(),
                        review.getReviewId(),
                        review.getProduct().getUser().getUserId()

                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ReviewResponseDto> getReviewsByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        List<Review> reviews = reviewRepository.findByProduct(product);
        return reviews.stream()
                .map(review -> new ReviewResponseDto(
                        review.getReviewTitle(),
                        review.getReviewContent(),
                        review.getProduct().getProductId(),
                        review.getUser().getUserId(),
                        review.getRating(),
                        review.getReviewId(),
                        review.getProduct().getUser().getUserId()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public SellerReviewPageDto getReviewsBySellerId(Long sellerId) {
        // 판매자가 판매한 상품 목록 조회
        List<Product> products = productRepository.findByUserUserId(sellerId);

        // 각 상품에 대한 리뷰 목록 조회 및 변환
        List<SellerReviewResponseDto> reviews = new ArrayList<>();
        String baseImageUrl = "/images/";
        for (Product product : products) {
            List<Review> productReviews = reviewRepository.findByProduct(product);
            for (Review review : productReviews) {
                reviews.add(new SellerReviewResponseDto(
                        product.getProductName(),
                        review.getReviewDate(),
                        review.getUser().getUserNickname(),
                        baseImageUrl + review.getUser().getUserImg(), // 이미지 경로에 base URL 추가
                        review.getRating(),
                        review.getReviewContent(),
                        review.getReviewTitle()
                ));
            }
        }

        // 총 판매 수 계산
        int totalSales = purchaseRepository.countBySellerId(sellerId);

        // 별점 수 계산
        int totalRatings = reviews.size();

        // 결과를 새로운 DTO에 담아서 반환
        return new SellerReviewPageDto(reviews, totalSales, totalRatings);
    }


}