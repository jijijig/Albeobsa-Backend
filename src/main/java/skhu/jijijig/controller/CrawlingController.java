package skhu.jijijig.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import skhu.jijijig.service.CrawlingService;

@Tag(name = "Crawling API", description = "크롤링 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CrawlingController {
    private final CrawlingService crawlingService;

    @GetMapping("/crawling/naver")
    public ResponseEntity<String> crawlNaverBody() {
        String bodyContent = crawlingService.crawlNaverBodyContent();
        return ResponseEntity.ok(bodyContent);
    }

    @GetMapping("/crawling/naver/save")
    public ResponseEntity<String> saveCrawledNaverBody() {
        crawlingService.saveNaverBodyContent();
        return ResponseEntity.ok("네이버 body 내용이 성공적으로 저장되었습니다.");
    }
}