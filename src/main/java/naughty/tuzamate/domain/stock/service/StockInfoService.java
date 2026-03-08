package naughty.tuzamate.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.service.support.StockApiRetryExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 주식 상품 기본 조회
 * 한투의 "상품 기본 조회" API를 통해 주식의 상품 이름(주식 이름)을 얻어온다.
 */

@Service
@RequiredArgsConstructor
public class StockInfoService {


    private final RestTemplate restTemplate;
    private final HantuApiTokenService hantuApiTokenService;
    private final ObjectMapper objectMapper;
    private final StockApiRetryExecutor stockApiRetryExecutor;

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    private String accessToken;

    // 주식의 상품이름을 얻어오는 메소드
    // marketCode 300 : 한국 주식, 512 : 미국 주식
    public StockInfoDto.InfoDto getStockInfo(String stockCode, String marketCode) {
        // 외부 조회 전용 서비스: DB 트랜잭션 없이 재시도만 적용
        return stockApiRetryExecutor.execute("Stock search-info", () -> {
            HttpHeaders headers = createHeaders();
            String url = "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info";

            HttpEntity<?> httpEntity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("PDNO", stockCode)
                    .queryParam("PRDT_TYPE_CD", marketCode);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            return parsingKrxInfo(response.getBody());
        });

    }

    private StockInfoDto.InfoDto parsingKrxInfo(String response) {

        StockInfoDto.InfoDto data = new StockInfoDto.InfoDto();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode node = rootNode.path("output");

            if (node != null) {
                StockInfoDto.InfoDto outputDto = new StockInfoDto.InfoDto();

                outputDto.setPrdtAbrvName(node.path("prdt_abrv_name").asText());
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
        httpHeaders.set("tr_id", "CTPF1604R");
        httpHeaders.set("custtype", "P");

        return httpHeaders;

    }
}
