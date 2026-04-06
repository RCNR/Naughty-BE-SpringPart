package naughty.tuzamate.domain.stock.service.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.service.support.StockApiRetryExecutor;
import naughty.tuzamate.domain.stock.service.support.StockRequestRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * 한국투자증권에서 주식현재가 시세 API를 통해 주식 현재가, PER, PBR, 주식 단축 종목코드, 업종 한글 종목명을
 * 조회하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class KrxInquireService {

    private final RestTemplate restTemplate;
    private final HantuApiTokenService hantuApiTokenService;
    private final ObjectMapper objectMapper;
    private final StockRequestRateLimiter stockRequestRateLimiter;
    private final StockApiRetryExecutor stockApiRetryExecutor;


    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    private String accessToken;

    public KrxDto.InquireDto getCurInquireInfo(String stockCode) {
        acquireRequestPermit();

        return stockApiRetryExecutor.execute("inquire", () -> {
            HttpHeaders headers = createHeaders();
            String url = "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price";

            HttpEntity<?> httpEntity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            return parsingCurInquireInfo(response.getBody());
        });
    }

    private void acquireRequestPermit() {
        try {
            stockRequestRateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KRX inquire request interrupted", e);
        }
    }

    private KrxDto.InquireDto parsingCurInquireInfo(String response) {

        KrxDto.InquireDto data = new KrxDto.InquireDto();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode node = rootNode.path("output");

            if (node != null) {
                KrxDto.InquireDto outputDto = new KrxDto.InquireDto();

                outputDto.setStckPrpr(node.path("stck_prpr").asText());
                outputDto.setPer(node.path("per").asText("0.00"));
                outputDto.setPbr(node.path("pbr").asText("0.00"));
                outputDto.setStckShrnIscd(node.path("stck_shrn_iscd").asText());
                outputDto.setBstpKorIsnm(node.path("bstp_kor_isnm").asText());
                data = outputDto;
            }
            return data;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders() {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        accessToken = hantuApiTokenService.getCurrentAccessToken();
        httpHeaders.setBearerAuth(accessToken);
        httpHeaders.set("appkey", appKey);
        httpHeaders.set("appsecret", appSecret);
        httpHeaders.set("tr_id", "FHKST01010100");
        httpHeaders.set("custtype", "P");

        return httpHeaders;


    }


}
