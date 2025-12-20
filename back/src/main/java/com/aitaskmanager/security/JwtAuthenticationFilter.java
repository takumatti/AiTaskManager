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
        String userId = null; // subject: user_id（文字列）
        Integer uid = null;     // claim: uid（内部数値ID）
        Integer planId = null;  // claim: plan_id（任意）

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
            try {
                userId = tokenProvider.getUserIdStringFromToken(token);
                uid = tokenProvider.getUserIdFromToken(token);
                // plan_id は存在しない場合があるため Number/String を安全に解釈
                Object planClaim = tokenProvider.getClaim(token, "plan_id");
                if (planClaim instanceof Integer i) {
                    planId = i;
                } else if (planClaim instanceof Number n) {
                    planId = n.intValue();
                } else if (planClaim instanceof String s) {
                    try { planId = Integer.valueOf(s); } catch (NumberFormatException ignored) {}
                }
            } catch (ExpiredJwtException e) {
                // 有効期限切れのトークンは401を返して終了（フロントが自動ログアウト）
                response.setContentType("application/json; charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"message\":\"トークンの有効期限が切れています\"}");
                return;
            } catch (UnsupportedJwtException | MalformedJwtException | SecurityException | IllegalArgumentException e) {
                // 不正なトークンは認証なしで後段へ（permitAllのパスは通る、保護パスは401へ）
                userId = null;
                uid = null;
                planId = null;
            }
        }

        // トークンが有効で、認証されていない場合のみ認証処理を行う
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

            if (tokenProvider.validateToken(token)) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                // details には WebAuthenticationDetails に加えて JWT クレームの抜粋（uid/plan_id）を載せる
                var webDetails = new WebAuthenticationDetailsSource().buildDetails(request);
                java.util.Map<String, Object> details = new java.util.HashMap<>();
                details.put("web", webDetails);
                if (uid != null) details.put("uid", uid);
                if (planId != null) details.put("plan_id", planId);
                auth.setDetails(details);

                // SecurityContextに認証情報をセット
                SecurityContextHolder.getContext().setAuthentication(auth);

                // uid（内部数値ID）をリクエスト属性にセット（後段で取り出し可能）
                if (uid != null) {
                    request.setAttribute("X-User-Id", uid);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * /api/auth/** 配下は認証不要のためフィルターをスキップ
     * 
     * @param request HTTPリクエスト
     * @return スキップする場合はtrue、そうでなければfalse
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path != null && path.startsWith("/api/auth/");
    }
}
