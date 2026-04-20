package naughty.tuzamate.domain.stock.service.compare.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.entity.NasdaqStockInfo;
import naughty.tuzamate.domain.stock.repository.NasdaqStockInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNasdaqPersistenceService {

    private final NasdaqStockInfoRepository nasdaqStockInfoRepository;

    @Transactional
    public void replaceAllNasdaqStocks(List<NasdaqStockInfo> stockInfos) {
        nasdaqStockInfoRepository.deleteAllInBatch();

        if (!stockInfos.isEmpty()) {
            nasdaqStockInfoRepository.saveAll(stockInfos);
        }
    }
}
