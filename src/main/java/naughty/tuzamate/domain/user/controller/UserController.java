package naughty.tuzamate.domain.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import naughty.tuzamate.domain.user.service.UserService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "일반 로그인 및 회원가입", description = "소셜 로그인이 아닌 일반 로그인으로 로그인 및 가입")
public class UserController {

    private final UserService userService;

    /*
    @PostMapping("/login")
    public CustomResponse<?> login(@RequestBody UserRequestDTO.UserLoginDTO loginDTO) {
        UserResponseDTO.UserTokenDTO loginResult = userService.login(loginDTO);
        refreshTokenService.saveRefreshToken(loginResult.getUserId(), loginResult.getRefreshToken(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(loginResult.getRefreshTokenExpire().getTime()), ZoneId.of("Asia/Seoul")));
        return CustomResponse.onSuccess(loginResult);
    }

    @PostMapping("/signUp")
    public CustomResponse<?> singUp(@RequestBody UserRequestDTO.UserSignUpDTO signUpDTO) {
        UserResponseDTO.UserTokenDTO signUpResult = userService.signUp(signUpDTO);
        return CustomResponse.onSuccess(signUpResult);
    }
    */
}
