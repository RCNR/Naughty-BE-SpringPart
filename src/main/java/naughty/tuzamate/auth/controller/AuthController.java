package naughty.tuzamate.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import naughty.tuzamate.auth.service.AuthService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "인증", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;

    /*
    @GetMapping("/auth/logout")
    @Operation(summary = "로그아웃", description = "로그아웃을 수행합니다.")
    public CustomResponse<?> logout(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(bearer);
        return CustomResponse.onSuccess(AuthSuccessCode.LOGOUT_SUCCESS_CODE);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "토큰 재발급", description = "만료된 액세스 토큰을 재발급받습니다.")
    public CustomResponse<?> reissueToken(@CookieValue("refreshToken") String refreshToken, HttpServletResponse response) {
        TokenResponse.TokenDto tokenDto = authService.reissueToken(refreshToken);
        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenDto.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(jwtProvider.getRefreshExpiration())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return CustomResponse.onSuccess(AuthSuccessCode.ACCESS_TOKEN_REISSUE_SUCCESS_CODE);
    }
    */
}
