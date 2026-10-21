package com.bandup.web.admin;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deny-by-default access control for the admin area.
 *
 * <p>Guards both the admin pages ({@code /admin/**}) and the admin API
 * ({@code /api/v1/admin/**}). A servlet filter is used (rather than a handler
 * interceptor) so the admin API is denied even before its controllers exist.
 * Non-admins get {@code 403} on the API and a redirect to the app root on pages.
 */
@Component
public class AdminAccessFilter extends OncePerRequestFilter {

    private final AdminAccess adminAccess;

    public AdminAccessFilter(AdminAccess adminAccess) {
        this.adminAccess = adminAccess;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        boolean guarded = path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/api/v1/admin");
        if (guarded && !adminAccess.isAdmin(request)) {
            if (path.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } else {
                response.sendRedirect((ctx == null ? "" : ctx) + "/");
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
