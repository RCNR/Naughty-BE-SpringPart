package naughty.tuzamate.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.dto.StockInfoDto;
import naughty.tuzamate.domain.stock.dto.krx.KrxDto;
import naughty.tuzamate.domain.stock.dto.nasdaq.NasdaqDto;
import naughty.tuzamate.domain.stock.error.StockErrorCode;
import naughty.tuzamate.domain.stock.service.common.KrxFinancialService;
import naughty.tuzamate.domain.stock.service.common.KrxInquireService;
import naughty.tuzamate.domain.stock.service.common.StockCodeService;
import naughty.tuzamate.domain.stock.service.common.StockInfoService;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncKrxService;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncNasdaqService;
import naughty.tuzamate.domain.stock.service.compare.async.AsyncNasdaqStockFetcher;
import naughty.tuzamate.global.apiPayload.CustomResponse;
import naughty.tuzamate.global.success.GeneralSuccessCode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "주식 관련 API", description = "주식 코드 저장 및 관련 작업을 수행하는 API")
@RequestMapping("/api")
public class StockController {

    private final StockCodeService stockCodeService;
    private final AsyncNasdaqService asyncNasdaqService;
    private final AsyncKrxService krxService;
    private final KrxInquireService krxInquireService;
    private final KrxFinancialService krxFinancialService;
    private final StockInfoService stockInfoService;
    private final AsyncNasdaqStockFetcher asyncNasdaqStockFetcher;

    @PostMapping("/post-stock-codes")
    @Operation(summary = "주식 코드 저장", description = "코스피, 코스닥, 나스닥 주식 코드를 저장합니다.")
    public CustomResponse<?> saveStockCodes() {

        try {
            stockCodeService.codeSaveProcess();
            return CustomResponse.onSuccess("주식 코드 저장 완료");
        } catch (Exception e) {
            log.error("주식 코드 저장 중 오류 발생: {}", e.getMessage(), e);
            return CustomResponse.onFail(StockErrorCode.STOCK_CODE_SAVE_ERROR);
        }
    }

    @PostMapping("/nasdaq/{nasdaqStockCode}")
    @Operation(summary = "수동으로 나스닥 주식 한 개의 기본 정보 가져오기",
            description = "나스닥 주식 한 개의 기본 정보를 가져옵니다. 주식 코드를 입력해야 합니다.")
    public NasdaqDto.NasdaqInfoDto getNasdaqStockInfo(@PathVariable("nasdaqStockCode") String nasdaqStockCode) {

            return asyncNasdaqStockFetcher.getCurrentNasdaqInfo(nasdaqStockCode);
    }

    @PostMapping("/nasdaq/all")
    @Operation(summary = "나스닥 주식 전체 정보 가져오기",
            description = "나스닥 주식 전체 정보를 가져오고 저장합니다. 주식 코드를 입력하지 않아도 됩니다.")
    public CustomResponse<?> getAllNasdaqStockInfo() {

        asyncNasdaqService.saveNasdaqStocksInfo();
        return CustomResponse.onSuccess(GeneralSuccessCode.CREATED, "나스닥 주식 전체 정보 저장 완료");
    }

    @PostMapping("/krx/all")
    @Operation(summary = "KRX(코스피, 코스닥)의 정보 가져오고 저장",
            description = "주식 현재가, per, pbr, 종목코드, 업종, EPS, 상품 이름을 가져오고 저장합니다")
    public CustomResponse<?> getAllKrxStockInfo() {

        krxService.saveKrxStocksInfo();
        return CustomResponse.onSuccess(GeneralSuccessCode.CREATED, "KRX 주식 전체 정보 저장 완료");

    }

    @PostMapping("/inquire/{krxStockCode}")
    @Operation(summary = "수동으로 KRX 주식 한 개의 inquire 정보 가져오기",
            description = "KRX 주식 한 개의 inquire 정보를 가져옵니다. 주식 코드를 입력해야 합니다.")
    public KrxDto.InquireDto getKrxStockInfoDto(@PathVariable("krxStockCode") String krxStockCode) {

        return krxInquireService.getCurInquireInfo(krxStockCode);
    }

    @PostMapping("/financial/{krxStockCode}")
    @Operation(summary = "수동으로 KRX 주식 한 개의 financial 정보 가져오기",
            description = "KRX 주식 한 개의 financial 정보를 가져옵니다. 주식 코드를 입력해야 합니다.")
    public KrxDto.FinancialDto getKrxFinancialInfoDto(@PathVariable("krxStockCode") String krxStockCode) {

        return krxFinancialService.getCurFinancialInfo(krxStockCode);
    }

    @PostMapping("/stock-info/{stockCode}/{marketCode}")
    @Operation(summary = "수동으로 주식 한 개의 기본 정보 가져오기. 주식 이름만 가져오게 됩니다.",
            description = "주식 한 개의 기본 정보를 가져옵니다. 주식 코드와 시장 코드를 입력해야 합니다." +
                    " 한국 시장 코드는 '300'으로 입력하면 KRX, '512'으로 입력하면 NASDAQ입니다.")
    public StockInfoDto.InfoDto getKrxStockInfo(@PathVariable("stockCode") String stockCode,
                                                @PathVariable("marketCode") String marketCode) {

        return stockInfoService.getStockInfo(stockCode, marketCode);
    }
}
