package skhu.jijijig.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.By;
import skhu.jijijig.domain.dto.CrawlingDTO;
import skhu.jijijig.domain.model.Crawling;
import skhu.jijijig.repository.crawling.CrawlingRepository;

@Service
@RequiredArgsConstructor
public class CrawlingService {
    private final CrawlingRepository crawlingRepository;

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);

    @Scheduled(fixedRate = 600000) // 10분마다 실행
    public void scheduleCrawlingTasks() {
        if (executor.isShutdown() || executor.isTerminated()) {
            restartExecutor();
        }
        executor.schedule(this::performCrawlingForPpomppuDomestic, 0, TimeUnit.SECONDS);
        executor.schedule(this::performCrawlingForPpomppuOverseas, 0, TimeUnit.SECONDS);
        executor.schedule(this::performCrawlingForQuasarzone, 0, TimeUnit.SECONDS);
        executor.schedule(this::performCrawlingForEomisae, 0, TimeUnit.SECONDS);
        executor.schedule(this::performCrawlingForRuliweb, 0, TimeUnit.SECONDS);
        executor.schedule(this::performCrawlingForCoolenjoy, 0, TimeUnit.SECONDS);
    }

    @Scheduled(fixedRate = 600000) // 10분마다 실행
    public void manageThreads() {
        System.out.println("Managing threads...");
        if (executor.isShutdown() || executor.isTerminated()) {
            restartExecutor();
        }
    }

    @Transactional
    @Async
    public void performCrawlingForPpomppuDomestic() {
        crawlWebsite("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", "뽐뿌(국내게시판)", "tbody > tr.baseList.bbs_new1");
    }

    @Transactional
    @Async
    public void performCrawlingForPpomppuOverseas() {
        crawlWebsite("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", "뽐뿌(해외게시판)", "tbody > tr.baseList.bbs_new1");
    }

    @Transactional
    @Async
    public void performCrawlingForQuasarzone() {
        crawlWebsite("https://quasarzone.com/bbs/qb_saleinfo", "퀘사이존", "tbody > tr");
    }

    @Transactional
    @Async
    public void performCrawlingForEomisae() {
        crawlWebsite("https://eomisae.co.kr/rt", "어미새", "div.card_el.n_ntc.clear");
    }

    @Transactional
    @Async
    public void performCrawlingForRuliweb() {
        crawlWebsite("https://bbs.ruliweb.com/news/board/1020", "루리웹", "tr.table_body.blocktarget");
    }

    @Transactional
    @Async
    public void performCrawlingForCoolenjoy() {
        crawlWebsite("https://coolenjoy.net/bbs/jirum", "쿨엔조이", "li.d-md-table-row.px-3.py-2.p-md-0.text-md-center.text-muted.border-bottom");
    }

    @Transactional(readOnly = true)
    public Page<CrawlingDTO> searchCrawling(String keyword, Pageable pageable) {
        Page<Crawling> crawlings = crawlingRepository.searchCrawlingWithPagination(keyword, pageable);
        return crawlings.map(CrawlingDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<CrawlingDTO> getTop10CrawlingsByRanking() {
        List<Crawling> crawlings = crawlingRepository.findTop10ByRecommendAndComment();
        return crawlings.stream()
                .map(CrawlingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CrawlingDTO> getAllCrawlingsSortedByDateTime(Pageable pageable) {
        Page<Crawling> crawlings = crawlingRepository.findAllByOrderByDateTimeDesc(pageable);
        return crawlings.stream()
                .map(CrawlingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CrawlingDTO> getCrawlingsSortedByLabelAndDateTime(String label, Pageable pageable) {
        Page<Crawling> crawlings = crawlingRepository.findByLabelOrderByDateTimeDesc(label, pageable);
        return crawlings.stream()
                .map(CrawlingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private void crawlWebsite(String url, String label, String ROWS) {
        WebDriver driver = setupChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        List<Crawling> crawlings = new ArrayList<>();
        try {
            driver.get(url);
            List<WebElement> rows = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector(ROWS)));
            crawlings.addAll(handleCrawlingByLabel(label, rows));
            crawlingRepository.saveAll(crawlings);
        } catch (Exception e) {
            System.err.println("크롤링 도중 오류 발생: " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    private List<Crawling> extractPpomppu(List<WebElement> rows, String label, int START, int MINUS,
                                          String OPEN, String TITLE, String NAME, String DATETIME,
                                          String IMAGE, String VIEWS,
                                          String COMMENTCNT, String RECOMMENDCNT) {
        List<Crawling> crawlings = new ArrayList<>();
        for (int i = START; i < rows.size() - MINUS; i++) {
            WebElement row = rows.get(i);
            boolean open = parseOpen(row, OPEN);
            try {
                String title = parseTitle(row, label, TITLE);
                String name = parseName(row, NAME);
                String image = parseImage(row, label, IMAGE);
                String link = parseLink(row, label, TITLE);
                String dateTime = parseDateTime(row, label, DATETIME);
                int views = parseViews(row, label, VIEWS);
                int recommendCnt = parseRecommendCnt(row, label, RECOMMENDCNT);
                String[] voteCnts = row.findElements(By.cssSelector(RECOMMENDCNT)).stream()
                        .findFirst()
                        .map(td -> td.getText().split(" - "))
                        .orElse(new String[]{"0", "0"});
                int unrecommendCnt = parseInteger(voteCnts.length > 1 ? voteCnts[1] : "0");
                int commentCnt = parseCommentCnt(row, COMMENTCNT);
                Crawling crawling = Crawling.of(label, title, name, image, link, dateTime, views, recommendCnt, unrecommendCnt, commentCnt, open);
                updateOrCreateCrawling(crawling, open);
            } catch (Exception e) {
                System.err.println("뽐뿌 데이터 추출 실패: " + e.getMessage());
            }
        }
        return crawlings;
    }


    private List<Crawling> extractQuasarzone(List<WebElement> rows, String label, int START, int MINUS,
                                             String OPEN, String TITLE, String NAME, String DATETIME,
                                             String IMAGE, String VIEWS,
                                             String COMMENTCNT, String RECOMMENDCNT) {
        List<Crawling> crawlings = new ArrayList<>();
        for (int i = START; i < rows.size() - MINUS; i++) {
            WebElement row = rows.get(i);
            boolean open = parseOpen(row, OPEN);
            try {
                String title = parseTitle(row, label, TITLE);
                String name = parseName(row, NAME);
                String image = parseImage(row, label, IMAGE);
                String link = parseLink(row, label, TITLE);
                String dateTime = parseDateTime(row, label, DATETIME);
                int views = parseViews(row, label, VIEWS);
                int recommendCnt = parseRecommendCnt(row, label, RECOMMENDCNT);
                int commentCnt = parseCommentCnt(row, COMMENTCNT);
                Crawling crawling = Crawling.of(label, title, name, image, link, dateTime, views, recommendCnt, 0, commentCnt, open);
                updateOrCreateCrawling(crawling, open);
            } catch (Exception e) {
                System.err.println("퀘사이존 데이터 추출 실패: " + e.getMessage());
            }
        }
        return crawlings;
    }

    private List<Crawling> extractEomisae(List<WebElement> rows, String label, int START, int MINUS,
                                          String OPEN, String TITLE, String NAME, String DATETIME,
                                          String IMAGE, String VIEWS,
                                          String COMMENTCNT, String RECOMMENDCNT) {
        List<Crawling> crawlings = new ArrayList<>();
        for (int i = START; i < rows.size() - MINUS; i++) {
            WebElement row = rows.get(i);
            boolean open = parseOpen(row, OPEN);
            try {
                String title = parseTitle(row, label, TITLE);
                String name = parseName(row, NAME);
                String image = parseImage(row, label, IMAGE);
                String link = parseLink(row, label, TITLE);
                String dateTime = parseDateTime(row, label, DATETIME);
                int views = parseViews(row, label, VIEWS);
                int recommendCnt = parseRecommendCnt(row, label, RECOMMENDCNT);
                int commentCnt = parseCommentCnt(row, COMMENTCNT);
                Crawling crawling = Crawling.of(label, title, name, image, link, dateTime, views, recommendCnt, 0, commentCnt, open);
                updateOrCreateCrawling(crawling, open);
            } catch (Exception e) {
                System.err.println("어미새 데이터 추출 실패: " + e.getMessage());
            }
        }
        return crawlings;
    }

    private List<Crawling> extractRuliweb(List<WebElement> rows, String label, int START, int MINUS,
                                          String OPEN, String TITLE, String NAME, String DATETIME,
                                          String IMAGE, String VIEWS,
                                          String COMMENTCNT, String RECOMMENDCNT) {
        List<Crawling> crawlings = new ArrayList<>();
        for (int i = START; i < rows.size() - MINUS; i++) {
            WebElement row = rows.get(i);
            boolean open = parseOpen(row, OPEN);
            try {
                String title = parseTitle(row, label, TITLE);
                String name = parseName(row, NAME);
                String link = parseLink(row, label, TITLE);
                String image = parseImage(row, label, IMAGE);
                String dateTime = parseDateTime(row, label, DATETIME);
                int views = parseViews(row, label, VIEWS);
                int recommendCnt = parseRecommendCnt(row, label, RECOMMENDCNT);
                int commentCnt = parseCommentCnt(row, COMMENTCNT);
                Crawling crawling = Crawling.of(label, title, name, image, link, dateTime, views, recommendCnt, 0, commentCnt, open);
                updateOrCreateCrawling(crawling, open);
            } catch (Exception e) {
                System.err.println("루리웹 데이터 추출 실패: " + e.getMessage());
            }
        }
        return crawlings;
    }

    // parseLink
    private List<Crawling> extractCoolenjoy(List<WebElement> rows, String label, int START, int MINUS,
                                            String OPEN, String TITLE, String NAME, String DATETIME,
                                            String IMAGE, String VIEWS,
                                            String COMMENTCNT, String RECOMMENDCNT) {
        List<Crawling> crawlings = new ArrayList<>();
        for (int i = START; i < rows.size() -MINUS; i++) {
            WebElement row = rows.get(i);
            boolean open = parseOpen(row, OPEN);
            try {
                String title = parseTitle(row, label, TITLE);
                String name = parseName(row, NAME);
                String link = parseLink(row, label, TITLE);
                String image = parseImage(row, label, IMAGE);
                String dateTime = parseDateTime(row, label, DATETIME);
                int views = parseViews(row, label, VIEWS);
                int recommendCnt = parseRecommendCnt(row, label, RECOMMENDCNT);
                int commentCnt = parseCommentCnt(row, COMMENTCNT);
                Crawling crawling = Crawling.of(label, title, name, image, link, dateTime, views, recommendCnt, 0, commentCnt, open);
                updateOrCreateCrawling(crawling, open);
            } catch (Exception e) {
                System.err.println("쿨엔조이 데이터 추출 실패: " + e.getMessage());
            }
        }
        return crawlings;
    }

    private List<Crawling> handleCrawlingByLabel(String label, List<WebElement> rows) {
        List<Crawling> crawlings = new ArrayList<>();
        if (label.startsWith("뽐뿌")) {
            crawlings.addAll(extractPpomppu(rows, label, 0, 4,
                    "img[src*='/zboard/skin/DQ_Revolution_BBS_New1/end_icon.PNG']",
                    "a.baseList-title", "a.baseList-name", "time.baseList-time",
                    "a.baseList-thumb img", "td.baseList-space.baseList-views",
                    "span.baseList-c", "td.baseList-space.baseList-rec"));
        } else if (label.startsWith("퀘사이존")) {
            crawlings.addAll(extractQuasarzone(rows, label, 0, 0,
                    "span.label.done",
                    "a.subject-link", "div.user-nick-text", "span.date",
                    "a.thumb img", "span.count",
                    "span.ctn-count", "No recommendCnt"));
        } else if (label.startsWith("어미새")) {
            crawlings.addAll(extractEomisae(rows, label, 0, 0,
                    "open",
                    "h3 a.pjax", "div.info", "p > span:nth-child(2)",
                    "img.tmb", "span.fr:nth-child(1)",
                    "span.fr:nth-child(1)", "span.fr:nth-child(3)"));
        } else if (label.startsWith("루리웹")) {
            crawlings.addAll(extractRuliweb(rows, label, 4, 0,
                    "open",
                    "a.deco", "td.writer.text_over", "td.time",
                    "No image", "td.hit",
                    "a.num_reply span.num", "td.recomd"));
        } else if (label.startsWith("쿨엔조이")) {
            crawlings.addAll(extractCoolenjoy(rows, label, 0, 0,
                    ".fa-lock",
                    "div.na-item", "a.sv_member", "div.float-left.float-md-none.d-md-table-cell.nw-6.nw-md-auto.f-sm.font-weight-normal.py-md-2.pr-md-1",
                    "No image", "div.float-left.float-md-none.d-md-table-cell.nw-4.nw-md-auto.f-sm.font-weight-normal.py-md-2.pr-md-1",
                    "span.count-plus", "span.rank-icon_vote"));
        }
        return crawlings;
    }

    private boolean parseOpen(WebElement row, String OPEN) {
        if (OPEN.equals("open")) {
            return true;
        } else {
            return row.findElements(By.cssSelector(OPEN)).isEmpty();
        }
    }

    private String parseName(WebElement row, String NAME) {
        return Optional.of(row.findElement(By.cssSelector(NAME)).getText()).orElse("No name");
    }

    private String parseTitle(WebElement row, String label, String TITLE) {
        if (label.equals("쿨엔조이")) {
            return Optional.ofNullable(row.findElement(By.cssSelector(TITLE)).getText())
                    .map(t -> t.split("\\n")[0])
                    .orElse("");
        } else {
            return row.findElement(By.cssSelector(TITLE)).getText();
        }
    }

    private String parseLink(WebElement row, String label, String TITLE) {
        if (label.equals("쿨엔조이")) {
            return row.findElement(By.cssSelector("div.na-item a")).getAttribute("href");
        } else {
            return row.findElement(By.cssSelector(TITLE)).getAttribute("href");
        }
    }

    private String parseImage(WebElement row, String label, String IMAGE) {
        if (label.equals("루리웹")) {
            return "https://img.ruliweb.com/img/2016/common/ruliweb_bi.png";
        } else if (label.equals("쿨엔조이")) {
            return "https://coolenjoy.net/theme/BS4-Basic/storage/image/logo-test.svg";
        } else {
            return Optional.of(row.findElement(By.cssSelector(IMAGE)).getAttribute("src"))
                    .map(src -> src.startsWith("//") ? "https:" + src : src).orElse("No image");
        }
    }

    private String parseDateTime(WebElement row, String label, String DATETIME) {
        String dateTime = row.findElement(By.cssSelector(DATETIME)).getText();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
        if (label.startsWith("뽐뿌")) {
            if (dateTime.contains(":")) { // 시간 포맷이 들어오면 (예: "20:18:16")
                return today.format(dateFormatter) + " " + dateTime;
            } else if (dateTime.contains("/")) { // 날짜 포맷이 들어오면 (예: "24/05/11")
                String[] parts = dateTime.split("/");
                LocalDate date = LocalDate.of(today.getYear(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                return date.format(dateFormatter) + " 00:00:00";
            }
        } else if (label.startsWith("퀘사이존") || label.startsWith("루리웹")) {
            if (dateTime.contains(":")) { // 시간 포맷 (예: "11:53")
                return today.format(dateFormatter) + " " + dateTime + ":00"; // "2024-05-12 11:53:00"
            } else if (dateTime.contains("-")) { // 날짜 포맷 (예: "05-11")
                String[] parts = dateTime.split("-");
                LocalDate date = LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                return date.format(dateFormatter) + " 00:00:00"; // "2024-05-11 00:00:00"
            } else if (dateTime.contains(".")) {
                String[] parts = dateTime.split("\\.");
                LocalDate date = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                return date.format(dateFormatter) + " 00:00:00";
            }
        } else if (label.startsWith("어미새")) {
            if (dateTime.contains(".")) {
                String[] parts = dateTime.split("\\.");
                LocalDate date = LocalDate.of(today.getYear(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                if (date.isEqual(today)) { // 날짜가 오늘 날짜인 경우
                    return now.format(timeFormatter);
                } else { // 어제 날짜 또는 그 이전 날짜인 경우
                    return date.format(dateFormatter) + " 00:00:00";
                }
            }
        } else if (label.startsWith("쿨엔조이")) {
            dateTime = dateTime.replaceAll("등록일\\s+", ""); // "등록일"과 모든 공백(공백, 탭, 개행 포함) 제거
            if (dateTime.contains(":")) { // 시간 포맷 (예: "11:00")
                return today.format(dateFormatter) + " " + dateTime + ":00"; // "2024-05-12 11:00:00"
            } else if (dateTime.contains(".")) { // 날짜 포맷 (예: "05.11")
                String[] parts = dateTime.split("\\.");
                LocalDate date = LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                return date.format(dateFormatter) + " 00:00:00"; // "2024-05-11 00:00:00"
            }
        }
        return today.format(timeFormatter);
    }

    private int parseViews(WebElement row, String label, String VIEWS) {
        if (label.equals("퀘사이존")) {
            return Optional.ofNullable(row.findElement(By.cssSelector(VIEWS)).getText())
                    .map(s -> s.endsWith("k") ? (int)(Double.parseDouble(s.replace("k", "")) * 1000) : Integer.parseInt(s))
                    .orElse(0);
        } else {
            return parseInteger(row.findElement(By.cssSelector(VIEWS)).getText());
        }
    }

    private int parseRecommendCnt(WebElement row, String label, String RECOMMENDCNT) {
        if (label.equals("퀘사이존")) {
            return 0;
        } else {
            return row.findElements(By.cssSelector(RECOMMENDCNT)).stream()
                    .findFirst()
                    .map(td -> td.getText().split("-")[0].trim())
                    .map(this::parseInteger)
                    .orElse(0);
        }
    }

    private int parseCommentCnt(WebElement row, String COMMENTCNT) {
        return row.findElements(By.cssSelector(COMMENTCNT)).stream()
                .findFirst()
                .map(element -> parseInteger(element.getText()))
                .orElse(0);
    }

    private int parseInteger(String text) {
        try {
            return Integer.parseInt(text.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateOrCreateCrawling(Crawling crawling, boolean open) {
        Optional<Crawling> existing = crawlingRepository.findByLink(crawling.getLink());
        if (existing.isPresent()) {
            Crawling existingCrawling = existing.get();
            if (existingCrawling.isOpen() != open) {
                existingCrawling.updateOpen(open);
                crawlingRepository.save(existingCrawling);
            }
        } else {
            crawlingRepository.save(crawling);
        }
    }

    private WebDriver setupChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage", "--disable-extensions", "--disable-popup-blocking", "--start-maximized", "--window-size=1920,1080", "user-agent=Mozilla/5.0...", "--disable-infobars", "--disable-browser-side-navigation", "--disable-setuid-sandbox");
        options.setCapability("goog:loggingPrefs", java.util.Collections.singletonMap("browser", "ALL"));
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver(options);
    }

    private synchronized void restartExecutor() {
        if (!executor.isShutdown() && !executor.isTerminated()) {
            executor.shutdown(); // 기존 작업이 완료될 때까지 기다림
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow(); // 60초 후에도 종료되지 않으면 강제 종료
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        executor = Executors.newScheduledThreadPool(10);
    }
}