package naughty.tuzamate.domain.stock.service.compare.async;

import lombok.RequiredArgsConstructor;
import naughty.tuzamate.domain.stock.entity.KrxStockInfo;
import naughty.tuzamate.domain.stock.repository.KrxStockInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AsyncKrxPersistenceService {

    private final KrxStockInfoRepository krxStockInfoRepository;

    @Transactional
    public void replaceAllKrxStocks(List<KrxStockInfo> stockInfos, int batchSize) {
        krxStockInfoRepository.deleteAllInBatch();

        List<KrxStockInfo> batchBuffer = new ArrayList<>(batchSize);
        for (KrxStockInfo stockInfo : stockInfos) {
            batchBuffer.add(stockInfo);
            if (batchBuffer.size() >= batchSize) {
                flushBatch(batchBuffer);
            }
        }

        flushBatch(batchBuffer);
    }

    private void flushBatch(List<KrxStockInfo> batchBuffer) {
        if (batchBuffer.isEmpty()) {
            return;
        }

        krxStockInfoRepository.saveAll(batchBuffer);
        batchBuffer.clear();
    }
}
