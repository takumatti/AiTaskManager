package com.aitaskmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.UnsupportedJwtException;

/**
 * 各HTTPリクエストのヘッダーにあるJWTを検証するフィルター
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    /** 
     * 各リクエストでJWTを検証し、認証情報を設定する
     * 
     * @param request HTTPリクエスト
     * @param response HTTPレスポンス
     * @param chain フィルターチェーン
     * @throws ServletException 例外
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Authorizationヘッダーからトークン取得
        String header = request.getHeader("Authorization");
        String token = null;
        String username = null;
        Integer userId = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
            try {
                username = tokenProvider.getUsernameFromToken(token);
                userId = tokenProvider.getUserIdFromToken(token);
            } catch (ExpiredJwtException e) {
                // 有効期限切れのトークンは401を返して終了（フロントが自動ログアウト）
                response.setContentType("application/json; charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"message\":\"トークンの有効期限が切れています\"}");
                return;
            } catch (UnsupportedJwtException | MalformedJwtException | SecurityException | IllegalArgumentException e) {
                // 不正なトークンは認証なしで後段へ（permitAllのパスは通る、保護パスは401へ）
                username = null;
                userId = null;
            }
        }

        // トークンが有効で、認証されていない場合のみ認証処理を行う
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (tokenProvider.validateToken(token)) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContextに認証情報をセット
                SecurityContextHolder.getContext().setAuthentication(auth);

                // userId をリクエスト属性にセット（後段で取り出し可能）
                if (userId != null) {
                    request.setAttribute("X-User-Id", userId);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * /api/auth/** 配下は認証不要のためフィルターをスキップ
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path != null && path.startsWith("/api/auth/");
    }
}
