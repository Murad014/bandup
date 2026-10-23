package com.bandup.api.identity.web;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.identity.application.GetMe;
import com.bandup.api.identity.application.Login;
import com.bandup.api.identity.application.Logout;
import com.bandup.api.identity.application.RefreshSession;
import com.bandup.api.identity.application.RegisterLearner;
import com.bandup.api.identity.application.SignedIn;
import com.bandup.api.identity.application.UserView;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.web.ApiProblems;

/** Sign-up, sign-in and session endpoints (requirement 8). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private final RegisterLearner registerLearner;
    private final Login login;
    private final RefreshSession refreshSession;
    private final Logout logout;
    private final GetMe getMe;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserView register(@RequestBody AuthRequests.Register body) {
        return registerLearner.register(new RegisterLearner.Command(
                body.email(), body.password(), body.displayName(), body.uiLanguage(), body.supportLanguage()));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@RequestBody AuthRequests.Login body, HttpServletRequest request) {
        // The direct peer's address. Behind a reverse proxy, configure forwarded headers (see the deployment notes).
        return signedIn(login.login(new Login.Command(body.email(), body.password(), request.getRemoteAddr())));
    }

    /** Called on every page load: the access token lives only in the page's memory. */
    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        try {
            return signedIn(refreshSession.refresh(refreshToken));
        } catch (UnauthenticatedException e) {
            ProblemDetail problem = ApiProblems.unauthenticated(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, RefreshCookie.clear())
                    .body(problem);
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        logout.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookie.clear()).build();
    }

    @GetMapping("/me")
    UserView me() {
        return currentUser.get()
                .map(caller -> getMe.get(caller.userId()))
                .orElseThrow(() -> new UnauthenticatedException("Sign in to continue."));
    }

    private static ResponseEntity<AuthResponse> signedIn(SignedIn signedIn) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.issue(signedIn.refreshToken(), signedIn.issuedAt(),
                        signedIn.refreshTokenExpiresAt()))
                .body(AuthResponse.of(signedIn));
    }
}
