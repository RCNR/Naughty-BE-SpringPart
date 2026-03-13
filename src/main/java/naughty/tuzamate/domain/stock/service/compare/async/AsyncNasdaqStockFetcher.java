package naughty.tuzamate.domain.stock.service.compare.async;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.nasdaq.NasdaqDto;
import naughty.tuzamate.domain.stock.entity.NasdaqStockInfo;
import naughty.tuzamate.domain.stock.service.common.StockInfoService;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNasdaqStockFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HantuApiTokenService hantuApiTokenService;
    private final StockInfoService stockInfoService;
    private final FilterStrategy filterStrategy;

    private final RateLimiter rateLimiter = RateLimiter.create(15.0, 1, TimeUnit.SECONDS);

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    @Async("taskExecutor")
    public CompletableFuture<Optional<NasdaqStockInfo>> fetchStock(String stockCode) {

        try {
            rateLimiter.acquire(3);


            HttpHeaders header = createHeaders();
            String url = "https://openapi.koreainvestment.com:9443/uapi/overseas-price/v1/quotations/price-detail";
            HttpEntity<?> httpEntity = new HttpEntity<>(header);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("AUTH", "")
                    .queryParam("EXCD", "NAS")
                    .queryParam("SYMB", stockCode);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            NasdaqDto.NasdaqInfoDto currentNasdaqInfo = parsingCurrentNasdaqInfo(response.getBody(), stockCode);

            // 한국 주식과 마찬가지로 필터링
            if (filterStrategy.shouldSkipNasdaq(currentNasdaqInfo)) {
                log.info("PER or PBR or EPS is zero: {}", stockCode);
                return CompletableFuture.completedFuture(Optional.empty());
            }

            StockInfoDto.InfoDto currentStockInfo = stockInfoService.getStockInfo(stockCode, "512");

            NasdaqStockInfo entity = currentNasdaqInfo.toEntity(currentNasdaqInfo, currentStockInfo);

            return CompletableFuture.completedFuture(Optional.of(entity));

        } catch (Exception e) {
            log.error("Nasdaq 정보 조회 중 오류 발생. StockCode: {}, Error: {}", stockCode, e.getMessage());
            return CompletableFuture.completedFuture(Optional.empty()); // 오류 발생 시 빈 Optional 반환
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        String accessToken = hantuApiTokenService.getCurrentAccessToken();

        httpHeaders.setBearerAuth(accessToken);
        httpHeaders.set("appkey", appKey);
        httpHeaders.set("appsecret", appSecret);
        httpHeaders.set("tr_id", "HHDFS76200200");
        httpHeaders.set("custtype", "P");

        return httpHeaders;
    }

    private NasdaqDto.NasdaqInfoDto parsingCurrentNasdaqInfo(String response, String stockCode)  {

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode node = rootNode.path("output");

            NasdaqDto.NasdaqInfoDto outputDto = new NasdaqDto.NasdaqInfoDto();
            outputDto.setCode(stockCode);
            outputDto.setPerx(node.path("perx").asText());
            outputDto.setPbrx(node.path("pbrx").asText());
            outputDto.setEpsx(node.path("epsx").asText());
            outputDto.setE_icod(node.path("e_icod").asText());
            outputDto.setLast(node.path("last").asText());

            return outputDto;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response: " + e.getMessage(), e);
        }
    }

    // 현재 나스닥 주식 정보 조회 메소드
    // 없어도 되지만 컨트롤러의 getNasdaqStockInfo 메소드의 나스닥 단일 코드로 조회하는 테스트 용 메소드이다.
    public NasdaqDto.NasdaqInfoDto getCurrentNasdaqInfo(String stockCode)  {


        HttpHeaders header = createHeaders();

        String url = "https://openapi.koreainvestment.com:9443/uapi/overseas-price/v1/quotations/price-detail";

        HttpEntity<?> httpEntity = new HttpEntity<>(header);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("AUTH", "")
                .queryParam("EXCD", "NAS")
                .queryParam("SYMB", stockCode);

        ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                httpEntity,
                String.class
        );

        return parsingCurrentNasdaqInfo(response.getBody(), stockCode);

    }
}
