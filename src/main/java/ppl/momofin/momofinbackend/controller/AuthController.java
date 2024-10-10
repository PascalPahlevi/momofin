package ppl.momofin.momofinbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ppl.momofin.momofinbackend.model.User;
import ppl.momofin.momofinbackend.request.RegisterRequest;
import ppl.momofin.momofinbackend.response.Response;
import ppl.momofin.momofinbackend.response.ErrorResponse;
import ppl.momofin.momofinbackend.response.AuthResponseSuccess;
import ppl.momofin.momofinbackend.service.LoggingService;
import ppl.momofin.momofinbackend.response.RegisterResponseSuccess;
import ppl.momofin.momofinbackend.service.UserService;
import ppl.momofin.momofinbackend.request.AuthRequest;
import ppl.momofin.momofinbackend.security.JwtUtil;

import static ppl.momofin.momofinbackend.controller.DocumentVerificationController.getUsername;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    private final LoggingService loggingService;

    @Autowired
    public AuthController(UserService userService, JwtUtil jwtUtil, LoggingService loggingService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loggingService = loggingService;
    }

    @PostMapping("/login")
    public ResponseEntity<Response> authenticateUser(@RequestBody AuthRequest authRequest, HttpServletRequest request) {
        String logName = "/auth/login";
        String sourceUrl = request.getRequestURL().toString() ;

        try {
            User authenticatedUser = userService.authenticate(
                    authRequest.getOrganizationName(),
                    authRequest.getUsername(),
                    authRequest.getPassword()
            );
            String jwt = jwtUtil.generateToken(authenticatedUser);

            loggingService.log(authenticatedUser.getUserId(), "INFO",
                    "Successful login for user: " + authenticatedUser.getUsername() +
                            " from organization: " + authRequest.getOrganizationName(),
                    logName, sourceUrl);

            AuthResponseSuccess response = new AuthResponseSuccess(authenticatedUser, jwt);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            loggingService.log(null, "ERROR",
                    "Failed login attempt for user: " + authRequest.getUsername() +
                            " from organization: " + authRequest.getOrganizationName(),
                    logName, sourceUrl);
            ErrorResponse response = new ErrorResponse(e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Response> registerUser(@RequestHeader("Authorization") String token, @RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        String logName = "/auth/register";
        String sourceUrl = request.getRequestURL().toString();

        String username = authenticateAndGetUsername(token);
        User user = userService.fetchUserByUsername(username);
        try {
            User registeredUser = userService.registerMember(
                    user.getOrganization(),
                    registerRequest.getUsername(),
                    registerRequest.getName(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword(),
                    registerRequest.getPosition()
            );
            loggingService.log(registeredUser.getUserId(), "INFO",
                    "User: " + registeredUser.getUsername() +
                            " with Name: " + registeredUser.getName() +
                            " using Email: " + registeredUser.getEmail() +
                            " with Position: " + registeredUser.getPosition(),
                    logName, sourceUrl);

            RegisterResponseSuccess response = new RegisterResponseSuccess(registeredUser);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            loggingService.log(null, "ERROR",
                    "Failed registration attempt for user: " + registerRequest.getUsername(),
                    logName, sourceUrl);

            ErrorResponse response = new ErrorResponse(e.getMessage());

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @GetMapping("/info")
    public ResponseEntity<User> getAuthenticatedUser(@RequestHeader("Authorization") String token) {
        String username = authenticateAndGetUsername(token);

        User user = userService.fetchUserByUsername(username);

        return ResponseEntity.ok(user);
    }

    private String authenticateAndGetUsername(String token) {
        return getUsername(token, jwtUtil);
    }
}
