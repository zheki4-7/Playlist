package zhedron.playlist.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import zhedron.playlist.config.filter.JwtFilter;
import zhedron.playlist.services.CustomOauth2UserService;
import zhedron.playlist.services.impl.UserDetailsImpl;
import zhedron.playlist.success.handlers.GoogleSuccessHandler;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String[] ALL = {"/login", "/refreshtoken",
            "/user/registration", "/user/{userId}", "/user/playlists/{userId}", "/user/update/{userId}", "/user/picture/{userId}",
            "/song/top", "/song/file/{songId}", "/song/perweek", "/song/{songId}", "/song/search", "/song/image/{songId}", "/song/status/{id}",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/refreshtoken"};

    private final JwtFilter jwtFilter;
    private final CustomOauth2UserService customOauth2UserService;
    private final GoogleSuccessHandler googleSuccessHandler;

    public SecurityConfig (JwtFilter jwtFilter, CustomOauth2UserService customOauth2UserService, GoogleSuccessHandler googleSuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.customOauth2UserService = customOauth2UserService;
        this.googleSuccessHandler = googleSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorizeRequests -> {
                    authorizeRequests
                            .requestMatchers("/user/block/{userId}", "/user/change_role/{userId}").hasAuthority("ADMIN")
                            .requestMatchers(ALL).permitAll()
                            .anyRequest().authenticated();
                }).addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> {
                    oauth2.userInfoEndpoint(userInfo ->
                        userInfo.userService(customOauth2UserService));
                    oauth2.successHandler(googleSuccessHandler);
                })
                .exceptionHandling(exceptions -> {
                    exceptions.authenticationEntryPoint((req, resp, e) -> {
                        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    });
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService myUserDetails() {
        return new UserDetailsImpl();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(myUserDetails());
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());

        return daoAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
