package com.article.article.service;

import com.article.article.component.SearchResultsProducer;
import com.article.article.dto.BigkindsRequestParam;
import com.article.article.dto.BigkindsResponse;
import com.article.article.dto.CompanySearchParam;
import com.article.article.entity.Article;
import com.article.article.mapper.ArticleMapper;
import com.article.article.repository.ArticleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class BigkindsArticleService {

    @Value("${bigkinds.api.url}")
    private String bigkindsUrl;

    private static final Logger log = LoggerFactory.getLogger(BigkindsArticleService.class);

    private final ArticleRepository articleRepository;
    private final ArticleMapper articleMapper;
    private final RestTemplate restTemplate;
    private final SearchResultsProducer searchResultsProducer;

    public BigkindsArticleService(ArticleRepository articleRepository, ArticleMapper articleMapper, RestTemplate restTemplate, SearchResultsProducer searchResultsProducer) {
        this.articleRepository = articleRepository;
        this.articleMapper = articleMapper;
        this.restTemplate = restTemplate;
        this.searchResultsProducer = searchResultsProducer;
    }

    // 빅카인즈 뉴스 검색로직
    public BigkindsResponse searchBigkindsArticle (CompanySearchParam searchParam) {
        try {
            if (searchParam.getCompanyName().isEmpty() || searchParam.getCeoName().isEmpty()) {
                log.info("회사명 또는 대표자명을 알 수 없습니다.");
            } else if (searchParam.getTermination().equals("CLOSED")) {
                log.info("수집을 진행하지 않습니다. 이유 : CLOSED");
            } else if (searchParam.getCorporateStatus().equals("살아있는 등기") || searchParam.getCorporateStatus().equals("회생절차")
                    || searchParam.getCorporateStatus().equals("보전관리")) {

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                BigkindsRequestParam requestParam = new BigkindsRequestParam(searchParam.getCompanyName() + " " + searchParam.getCeoName());

                HttpEntity<BigkindsRequestParam> requestEntity = new HttpEntity<>(requestParam, headers);

                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<BigkindsResponse> response = restTemplate.exchange(bigkindsUrl, HttpMethod.POST, requestEntity, BigkindsResponse.class);
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                BigkindsResponse bigkindsResponse = response.getBody();

                int total = bigkindsResponse.getReturnObject().getTotalHits();
                log.info("빅카인즈 기사 총 {}건", total);

                if (bigkindsResponse != null && bigkindsResponse.getReturnObject() != null && bigkindsResponse.getReturnObject().getDocuments() != null) {
                    for (BigkindsResponse.Document document : bigkindsResponse.getReturnObject().getDocuments()) {
                        String title = document.getTitle();
                        String link = document.getProviderLinkPage();

                        if (!isDuplicateNews(title, link)) {

                            Article news = articleMapper.bigkindsResponseToArticle(document);
                            news.setIdSeq(searchParam.getId_seq());

                            String searchResultJson = objectMapper.writeValueAsString(news);
                            //searchResultsProducer.sendSearchResults(searchResultJson);
                            articleRepository.save(news);
                        }
                    }
                }
            } else log.info("사업 활동 중이 아닌 기업 입니다.");
            return null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("검색 실패", e);
        }
    }

    private boolean isDuplicateNews(String title, String originLink) {
        return articleRepository.existsByTitleOrOriginLink(title, originLink);
    }
}
