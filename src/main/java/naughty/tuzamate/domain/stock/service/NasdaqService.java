package naughty.tuzamate.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.auth.hantu.service.HantuApiTokenService;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.nasdaq.NasdaqDto;
import naughty.tuzamate.domain.stock.entity.NasdaqStockCode;
import naughty.tuzamate.domain.stock.entity.NasdaqStockInfo;
import naughty.tuzamate.domain.stock.repository.NasdaqStockInfoRepository;
import naughty.tuzamate.domain.stock.repository.code.NasdaqCodeRepository;
import naughty.tuzamate.domain.stock.service.support.StockApiRetryExecutor;
import naughty.tuzamate.domain.stock.service.support.StockRequestRateLimiter;
import naughty.tuzamate.domain.stock.strategy.FilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NasdaqService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NasdaqCodeRepository nasdaqCodeRepository;
    private final NasdaqStockInfoRepository nasdaqStockInfoRepository;
    private final HantuApiTokenService hantuApiTokenService;
    private final StockInfoService stockInfoService;
    private final FilterStrategy filterStrategy;
    private final StockRequestRateLimiter stockRequestRateLimiter;
    private final StockApiRetryExecutor stockApiRetryExecutor;

    @Value("${stock.collect.batch-size:200}")
    private int batchSize;

    @Value("${tuza.api.APP_KEY}")
    private String appKey;

    @Value("${tuza.api.APP_SECRET_KEY}")
    private String appSecret;

    private String accessToken;

    private HttpHeaders createHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        accessToken = hantuApiTokenService.getCurrentAccessToken();

        httpHeaders.setBearerAuth(accessToken);
        httpHeaders.set("appkey", appKey);
        httpHeaders.set("appsecret", appSecret);
        httpHeaders.set("tr_id", "HHDFS76200200");
        httpHeaders.set("custtype", "P");

        return httpHeaders;
    }

    private NasdaqDto.NasdaqInfoDto parsingCurrentNasdaqInfo(String response, String stockCode) {
        NasdaqDto.NasdaqInfoDto data = new NasdaqDto.NasdaqInfoDto();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode node = rootNode.path("output");

            if (node != null) {
                NasdaqDto.NasdaqInfoDto outputDto = new NasdaqDto.NasdaqInfoDto();

                outputDto.setCode(stockCode);
                outputDto.setPerx(node.path("perx").asText());
                outputDto.setPbrx(node.path("pbrx").asText());
                outputDto.setEpsx(node.path("epsx").asText());
                outputDto.setE_icod(node.path("e_icod").asText());
                outputDto.setLast(node.path("last").asText());


                data = outputDto;
            }
            return data;
        } catch (Exception e) {
            log.error("error is : {}", e.getMessage());
            throw new RuntimeException();
        }
    }

    public NasdaqDto.NasdaqInfoDto getCurrentNasdaqInfo(String stockCode) {
        // 일시 오류 구간은 재시도하여 누락을 줄인다.
        return stockApiRetryExecutor.execute("NASDAQ price-detail", () -> {
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
        });

    }

    public void saveNasdaqStocksInfo() {
        List<NasdaqStockCode> stockCodeList = nasdaqCodeRepository.findAll();

        nasdaqStockInfoRepository.deleteAllInBatch();
        List<NasdaqStockInfo> batchBuffer = new ArrayList<>(batchSize);

        for (NasdaqStockCode stockCode : stockCodeList) {
            try {

                // sleep 대신 동기 RateLimiter로 요청 간격 제어
                stockRequestRateLimiter.acquire();

                NasdaqDto.NasdaqInfoDto currentNasdaqInfo = getCurrentNasdaqInfo(stockCode.getCode());
                StockInfoDto.InfoDto currentStockInfo = stockInfoService.getStockInfo(stockCode.getCode(), "512");

                if (filterStrategy.shouldSkipNasdaq(currentNasdaqInfo)) {
                    log.info("PER or PBR or EPS is zero: {}", stockCode.getCode());
                    continue; // 필터 전략에 의해 스킵된 경우 다음 주식 코드로 넘어감
                }

                NasdaqStockInfo entity = currentNasdaqInfo.toEntity(currentNasdaqInfo, currentStockInfo);

                batchBuffer.add(entity);
                flushBatchIfNeeded(batchBuffer);

                log.info("Saved stocks is : {}", stockCode.getCode());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 현재 스레드 인터럽트 상태 복구
                log.info("Thread Interrupted : {}", e.getMessage());
                break;
            } catch (Exception e) {
                log.info("Error stock code is {} : {} and pass!", stockCode.getCode(), e.getMessage());
            }

        }
        flushBatch(batchBuffer);
    }

    private void flushBatchIfNeeded(List<NasdaqStockInfo> batchBuffer) {
        if (batchBuffer.size() >= batchSize) {
            flushBatch(batchBuffer);
        }
    }

    private void flushBatch(List<NasdaqStockInfo> batchBuffer) {
        if (batchBuffer.isEmpty()) {
            return;
        }
        // 단건 save 반복 대신 배치 저장
        nasdaqStockInfoRepository.saveAll(batchBuffer);
        batchBuffer.clear();
    }
}
