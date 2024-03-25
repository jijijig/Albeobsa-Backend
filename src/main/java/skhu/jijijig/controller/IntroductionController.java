package skhu.jijijig.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import skhu.jijijig.domain.dto.ErrorResponseDTO;
import skhu.jijijig.domain.dto.IntroductionDTO;
import skhu.jijijig.domain.model.Introduction;
import skhu.jijijig.service.IntroductionService;

@Slf4j
@Tag(name = "Introduction API", description = "자기소개서 관련 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class IntroductionController {
    private final IntroductionService introductionService;

    @Operation(summary = "자기소개서 등록/수정", description = "사용자가 제출하기를 클릭하면 이 메소드가 호출된다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "자기소개서 등록/수정 성공", content = @Content(schema = @Schema(implementation = Introduction.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })
    @PostMapping("/introduction")
    public ResponseEntity<?> createOrUpdateIntroduction(@RequestBody IntroductionDTO introductionDTO) {
        try {
            Introduction introduction = introductionService.createOrUpdateIntroduction(introductionDTO);
            return ResponseEntity.ok(introduction);
        } catch (Exception e) {
            log.error("자기소개서 등록/수정 중 서버 오류 발생.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDTO("서버 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}