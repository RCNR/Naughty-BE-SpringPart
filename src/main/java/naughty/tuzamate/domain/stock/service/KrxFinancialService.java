package naughty.tuzamate.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.service.support.StockApiRetryExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국투자증권에서 주식현재가 시세 API를 통해 주식 EPS 값을
 * 조회하는 서비스입니다.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class KrxFinancialService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HantuApiTokenService hantuApiTokenService;
    private final StockApiRetryExecutor stockApiRetryExecutor;

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    private String accessToken;

    public KrxDto.FinancialDto getCurFinancialInfo(String stockCode) {
        // 외부 조회 전용 서비스: DB 트랜잭션 없이 재시도만 적용
        return stockApiRetryExecutor.execute("KRX financial-ratio", () -> {
            HttpHeaders httpHeaders = createHeaders();

            String url = "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/finance/financial-ratio";

            HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("FID_DIV_CLS_CODE", "1")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            return parsingCurrentFinanceInfo(response.getBody());
        });
    }

    private KrxDto.FinancialDto parsingCurrentFinanceInfo(String response) {

        KrxDto.FinancialDto data = new KrxDto.FinancialDto();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode arrayNode = jsonNode.path("output");
            /**
             * 한투의 재무비율 관련 API 응답을 보면  output: List[ResponseBodyoutput] 형태
             * output은 ResponseBody가 아닌 배열
             * 한 번더 열어야 한다.
             */

            JsonNode node = arrayNode.get(0);
            if (node != null) {
                KrxDto.FinancialDto outputDto = new KrxDto.FinancialDto();

                outputDto.setEps(node.path("eps").asText("0.00"));

                data = outputDto;
            }

            return data;

        } catch (Exception e) {
            log.error("FinanceService Error is : {}", e.getMessage());
            throw new RuntimeException();
        }
    }

    private HttpHeaders createHeaders() {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        accessToken = hantuApiTokenService.getCurrentAccessToken();
        httpHeaders.setBearerAuth(accessToken);
        httpHeaders.set("appkey", appKey);
        httpHeaders.set("appsecret", appSecret);
        httpHeaders.set("tr_id", "FHKST66430300");
        httpHeaders.set("custtype", "P");

        return httpHeaders;
    }
}
