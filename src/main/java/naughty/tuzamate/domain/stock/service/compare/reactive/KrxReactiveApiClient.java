package naughty.tuzamate.domain.stock.service.compare.reactive;

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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class KrxReactiveApiClient {

    private final WebClient webClient;
    private final HantuApiTokenService hantuApiTokenService;
    private final ObjectMapper objectMapper;
    private final ApiRateLimiter rateLimiter;
    private final ApiRetryExecutor retryExecutor;

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    public KrxReactiveApiClient(WebClient webClient,
                                HantuApiTokenService hantuApiTokenService,
                                ObjectMapper objectMapper,
                                ApiRateLimiter rateLimiter,
                                @Qualifier("reactiveRetryExecutor") ApiRetryExecutor retryExecutor) {
        this.webClient = webClient;
        this.hantuApiTokenService = hantuApiTokenService;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.retryExecutor = retryExecutor;
    }

    public Mono<KrxDto.InquireDto> getCurInquireInfo(String stockCode) {
        return rateLimiter.acquireReactive().then(
                retryExecutor.executeReactive("inquire",
                        webClient.get()
                                .uri("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price",
                                        uriBuilder -> uriBuilder
                                                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                                .queryParam("FID_INPUT_ISCD", stockCode)
                                                .build())
                                .headers(h -> setHeaders(h, "FHKST01010100"))
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::parseInquireInfo)
                )
        );
    }

    public Mono<KrxDto.FinancialDto> getCurFinancialInfo(String stockCode) {
        return rateLimiter.acquireReactive().then(
                retryExecutor.executeReactive("financial",
                        webClient.get()
                                .uri("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/finance/financial-ratio",
                                        uriBuilder -> uriBuilder
                                                .queryParam("FID_DIV_CLS_CODE", "1")
                                                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                                .queryParam("FID_INPUT_ISCD", stockCode)
                                                .build())
                                .headers(h -> setHeaders(h, "FHKST66430300"))
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::parseFinancialInfo)
                )
        );
    }

    public Mono<StockInfoDto.InfoDto> getStockInfo(String stockCode, String marketCode) {
        return rateLimiter.acquireReactive().then(
                retryExecutor.executeReactive("stock-info",
                        webClient.get()
                                .uri("https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info",
                                        uriBuilder -> uriBuilder
                                                .queryParam("PDNO", stockCode)
                                                .queryParam("PRDT_TYPE_CD", marketCode)
                                                .build())
                                .headers(h -> setHeaders(h, "CTPF1604R"))
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::parseStockInfo)
                )
        );
    }

    private void setHeaders(org.springframework.http.HttpHeaders headers, String trId) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hantuApiTokenService.getCurrentAccessToken());
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
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
