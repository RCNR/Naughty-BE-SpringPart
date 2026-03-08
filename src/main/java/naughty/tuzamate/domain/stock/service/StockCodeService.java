package naughty.tuzamate.domain.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import naughty.tuzamate.domain.stock.entity.NasdaqStockCode;
import naughty.tuzamate.domain.stock.entity.StockCode;
import naughty.tuzamate.domain.stock.repository.code.NasdaqCodeRepository;
import naughty.tuzamate.domain.stock.repository.code.StockCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StockCodeService {

    private final StockCodeRepository stockCodeRepository;
    private final NasdaqCodeRepository nasdaqCodeRepository;
    @Value("${stock.code.base-dir:${user.home}/stockcodes}")
    private String stockCodeBaseDir;


     // 한국투자증권의 자료를 이용해서 코스피, 코스닥, 나스닥 주식 코드를 DB에 저장하는 메소드
    public void codeSaveProcess() throws IOException{

        stockCodeRepository.deleteAllInBatch();

        String kospiZipUrl = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
        String kosdaqZipUrl = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
        String nasdaqZipUrl = "https://new.real.download.dws.co.kr/common/master/nasmst.cod.zip";

        // 로컬에서 사용시 주석 해제
      /*  String kospiZipPath = "C:\\Users\\namju\\Desktop\\기타 프젝\\주식 저장소\\kospi_code.mst.zip";
        String kosdaqZipPath = "C:\\Users\\namju\\Desktop\\기타 프젝\\주식 저장소\\kosdaq_code.mst.zip";
        String nasdaqZipPath = "C:\\Users\\namju\\Desktop\\기타 프젝\\주식 저장소\\nasmst.cod.zip";

        String extractDir = "/stockcodes/";
        String kospiTxtFileName = "kospi_code.mst";
        String kosdaqTxtFileName = "kosdaq_code.mst";
        String nasdaqTxtFileName = "nasmst.cod";

        download(kospiZipUrl, kospiZipPath);
        unzip(kospiZipPath, extractDir);
        List<String> kospiStockCodes = extractStockCode(extractDir + kospiTxtFileName);
        saveStockCodes(kospiStockCodes);

        download(kosdaqZipUrl, kosdaqZipPath);
        unzip(kosdaqZipPath, extractDir);
        List<String> kosdaqStockCodes = extractStockCode(extractDir + kosdaqTxtFileName);
        saveStockCodes(kosdaqStockCodes);

        download(nasdaqZipUrl, nasdaqZipPath);
        unzip(nasdaqZipPath, extractDir);
        List<String> nasdaqStockCodes = extractNasdaqStockCode(extractDir + nasdaqTxtFileName);
        saveNasdaqStockCodes(nasdaqStockCodes);*/

        Path extractDir = Paths.get(stockCodeBaseDir);
        Files.createDirectories(extractDir); // 없으면 생성

        Path kospiZipPath = extractDir.resolve("kospi_code.mst.zip");
        Path kosdaqZipPath = extractDir.resolve("kosdaq_code.mst.zip");
        Path nasdaqZipPath = extractDir.resolve("nasmst.cod.zip");

        download(kospiZipUrl, kospiZipPath.toString());
        unzip(kospiZipPath.toString(), extractDir.toString());
        List<String> kospiStockCodes = extractStockCode(extractDir.resolve("kospi_code.mst").toString());
        saveStockCodes(kospiStockCodes);

        download(kosdaqZipUrl, kosdaqZipPath.toString());
        unzip(kosdaqZipPath.toString(), extractDir.toString());
        List<String> kosdaqStockCodes = extractStockCode(extractDir.resolve("kosdaq_code.mst").toString());
        saveStockCodes(kosdaqStockCodes);

        download(nasdaqZipUrl, nasdaqZipPath.toString());
        unzip(nasdaqZipPath.toString(), extractDir.toString());
        List<String> nasdaqStockCodes = extractNasdaqStockCode(extractDir.resolve("NASMST.COD").toString());
        saveNasdaqStockCodes(nasdaqStockCodes);

    }

    public void saveNasdaqStockCodes(List<String> codes) {
        for (String code : codes) {
            nasdaqCodeRepository.save(NasdaqStockCode.of(code));
        }
    }

    public void saveStockCodes(List<String> codes) {
        for (String code : codes) {
            stockCodeRepository.save(StockCode.of(code));
        }
    }

    /**
     * zip 파일 다운로드
     * zip 압축 해제 후 TXT 파일 추출
     * 각 줄의 맨 앞 6자리 주식 코드 추출 - 코스피, 코스닥
     * DB에 저장
     */

    public void download(String urlInfo, String destFile) throws IOException {

        log.info("다운로드 시작: {}", urlInfo);

        URL url = new URL(urlInfo);

        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fos = new FileOutputStream(destFile)){
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

            log.info("다운로드 완료: {}", destFile);
        }





    }

    public void unzip(String zipFilePath, String destDir) throws IOException {

        File dir = new File(destDir);

        if (!dir.exists()) dir.mkdir();

        Path destDirPath = dir.toPath().normalize();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[8192];

            while ((zipEntry = zis.getNextEntry()) != null) {
                Path entryPath = destDirPath.resolve(zipEntry.getName()).normalize();

                if (!entryPath.startsWith(destDirPath)) {
                    throw new IOException("Invalid zip entry: " + zipEntry.getName());
                }

                File newFile = entryPath.toFile();

                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

//        byte[] buffer = new byte[1024];



        /*ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry zipEntry = zis.getNextEntry();

        while (zipEntry != null) {
            File newFile = new File(destDir, zipEntry.getName());
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;

            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();*/

    public List<String> extractStockCode(String txtFilePath) throws IOException {

        List<String> codes = new ArrayList<>();
        try(BufferedReader br = new BufferedReader(new FileReader(txtFilePath))) {
            String line;
            // 평범한 주식이 아닌 것은 제외시킨다
            while ((line = br.readLine()) != null) {
                if (line.startsWith("F")) continue;
                else if (line.startsWith("Q")) {
                    String code = line.substring(0, 7).trim();
                    codes.add(code);
                }
                else if (line.startsWith("J")) {
                    String code = line.substring(1, 7).trim();
                    codes.add(code);
                }
                else if (line.length() >= 6) {
                    String code = line.substring(0, 6).trim();
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    // 나스닥의 주식코드는 한국과 달라 따로 구성한다
    public List<String> extractNasdaqStockCode(String txtFilePath) throws IOException {

        List<String> codes = new ArrayList<>();

        try(BufferedReader br = new BufferedReader(new FileReader(txtFilePath));) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");

                if (tokens.length >= 5) {
                    codes.add(tokens[4]);
                }
            }
        }
        return codes;
    }
}
