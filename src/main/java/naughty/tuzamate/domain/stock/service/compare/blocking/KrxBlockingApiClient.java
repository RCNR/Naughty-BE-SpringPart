package naughty.tuzamate.domain.stock.service.compare.blocking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.service.compare.support.ApiRateLimiter;
import naughty.tuzamate.domain.stock.service.compare.support.ApiRetryExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class KrxBlockingApiClient {

    private final RestTemplate restTemplate;
    private final HantuApiTokenService hantuApiTokenService;
    private final ObjectMapper objectMapper;
    private final ApiRateLimiter rateLimiter;
    private final ApiRetryExecutor retryExecutor;

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    public KrxBlockingApiClient(RestTemplate restTemplate,
                                HantuApiTokenService hantuApiTokenService,
                                ObjectMapper objectMapper,
                                ApiRateLimiter rateLimiter,
                                @Qualifier("blockingRetryExecutor") ApiRetryExecutor retryExecutor) {
        this.restTemplate = restTemplate;
        this.hantuApiTokenService = hantuApiTokenService;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.retryExecutor = retryExecutor;
    }

    public KrxDto.InquireDto getCurInquireInfo(String stockCode) {
        acquirePermit();
        return retryExecutor.executeBlocking("inquire", () -> {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders("FHKST01010100"));
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .toUriString();
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseInquireInfo(response.getBody());
        });
    }

    public KrxDto.FinancialDto getCurFinancialInfo(String stockCode) {
        acquirePermit();
        return retryExecutor.executeBlocking("financial", () -> {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders("FHKST66430300"));
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/finance/financial-ratio")
                    .queryParam("FID_DIV_CLS_CODE", "1")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .toUriString();
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseFinancialInfo(response.getBody());
        });
    }

    public StockInfoDto.InfoDto getStockInfo(String stockCode, String marketCode) {
        acquirePermit();
        return retryExecutor.executeBlocking("stock-info", () -> {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders("CTPF1604R"));
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info")
                    .queryParam("PDNO", stockCode)
                    .queryParam("PRDT_TYPE_CD", marketCode)
                    .toUriString();
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseStockInfo(response.getBody());
        });
    }

    private void acquirePermit() {
        try {
            rateLimiter.acquireBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter interrupted", e);
        }
    }

    private HttpHeaders createHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hantuApiTokenService.getCurrentAccessToken());
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
        return headers;
    }

    private KrxDto.InquireDto parseInquireInfo(String response) {
        try {
            JsonNode node = objectMapper.readTree(response).path("output");
            KrxDto.InquireDto dto = new KrxDto.InquireDto();
            dto.setStckPrpr(node.path("stck_prpr").asText());
            dto.setPer(node.path("per").asText("0.00"));
            dto.setPbr(node.path("pbr").asText("0.00"));
            dto.setStckShrnIscd(node.path("stck_shrn_iscd").asText());
            dto.setBstpKorIsnm(node.path("bstp_kor_isnm").asText());
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing inquire response: " + e.getMessage(), e);
        }
    }

    private KrxDto.FinancialDto parseFinancialInfo(String response) {
        try {
            JsonNode arrayNode = objectMapper.readTree(response).path("output");
            JsonNode node = arrayNode.get(0);
            KrxDto.FinancialDto dto = new KrxDto.FinancialDto();
            if (node != null) {
                dto.setEps(node.path("eps").asText("0.00"));
            }
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing financial response: " + e.getMessage(), e);
        }
    }

    private StockInfoDto.InfoDto parseStockInfo(String response) {
        try {
            JsonNode node = objectMapper.readTree(response).path("output");
            StockInfoDto.InfoDto dto = new StockInfoDto.InfoDto();
            dto.setPrdtAbrvName(node.path("prdt_abrv_name").asText());
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing stock-info response: " + e.getMessage(), e);
        }
    }
}
