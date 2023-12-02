package com.example.springsecurity.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.springsecurity.model.Content;
import com.example.springsecurity.model.ERole;
import com.example.springsecurity.model.Role;
import com.example.springsecurity.model.User;
import com.example.springsecurity.model.dto.request.ContentRequest;
import com.example.springsecurity.model.dto.request.LoginRequest;
import com.example.springsecurity.model.dto.request.SignupRequest;
import com.example.springsecurity.model.dto.response.JwtResponse;
import com.example.springsecurity.model.dto.response.MessageResponse;
import com.example.springsecurity.repository.ContentRepository;
import com.example.springsecurity.repository.RoleRepository;
import com.example.springsecurity.repository.UserRepository;
import com.example.springsecurity.security.UserDetailsImpl;
import com.example.springsecurity.security.jwt.JwtUtils;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    ContentRepository contentRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;



    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream().map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity
                .ok(new JwtResponse( userDetails.getId(), userDetails.getUsername(), userDetails.getEmail(), roles,jwt));
    }



    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Tên người dùng đã được sử dụng!"));
        }
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email đã được sử dụng!"));
        }
        User user = new User(signUpRequest.getUsername(), signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRoles();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Không tìm thấy vai trò."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Không tìm thấy vai trò."));
                        roles.add(adminRole);

                        break;
                    case "mod":
                        Role modRole = roleRepository.findByName(ERole.ROLE_MODERATOR)
                                .orElseThrow(() -> new RuntimeException("Error: Không tìm thấy vai trò."));
                        roles.add(modRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Không tìm thấy vai trò."));
                        roles.add(userRole);
                }
            });
        }
        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Người dùng đã đăng ký thành công!"));
    }

    @GetMapping("/content")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public Page<Content> getAllUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Tạo đối tượng PageRequest để đại diện cho cấu hình phân trang
        PageRequest pageRequest = PageRequest.of(page, size);

        // Gọi phương thức findAll trên contentRepository với PageRequest đã được chỉ định
        return contentRepository.findAll(pageRequest);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> getContentByUsername(@RequestParam String username) {
        try {
            Optional<Content> existingContentOptional = contentRepository.findByUsername(username);

            if (existingContentOptional.isPresent()) {
                Content existingContent = existingContentOptional.get();
                return ResponseEntity.ok(existingContent);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Không tìm thấy nội dung với username: " + username));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Đã xảy ra lỗi khi truy vấn nội dung."));
        }
    }



    @PostMapping("/addcontent")
    @PreAuthorize(" hasRole('MODERATOR') or hasRole('ADMIN')")

    public ResponseEntity<?> registerContent(@Valid @RequestBody ContentRequest contentRequest) {
        try {

            // Lấy thông tin người dùng từ context security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = ((UserDetailsImpl) authentication.getPrincipal()).getUsername();

            // Tạo đối tượng Content và gán thông tin người dùng
            Content content = new Content(contentRequest.getTitle(), contentRequest.getContent());
            content.setUsername(username);
            contentRepository.save(content);
            return ResponseEntity.ok(new MessageResponse("Đăng bài thành công!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Đã xảy ra lỗi khi đăng bài."));
        }
    }

    @DeleteMapping("/delete/{contentId}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteContent(@PathVariable Long contentId) {
        try {
            // Kiểm tra xem nội dung có tồn tại hay không trước khi xóa
            if (!contentRepository.existsById(String.valueOf(contentId))) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Không tìm thấy nội dung."));
            }

            // Xóa nội dung
            contentRepository.deleteById(String.valueOf(contentId));

            return ResponseEntity.ok(new MessageResponse("Xóa bài thành công!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Đã xảy ra lỗi khi xóa bài."));
        }
    }


    @PutMapping("/update/{contentId}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> updateContent(@PathVariable Long contentId, @Valid @RequestBody ContentRequest contentRequest) {
        try {
            // Kiểm tra xem nội dung có tồn tại hay không
            if (!contentRepository.existsById(String.valueOf(contentId))) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Không tìm thấy nội dung."));
            }

            // Lấy nội dung từ cơ sở dữ liệu
            Content existingContent = contentRepository.findById(String.valueOf(contentId)).orElse(null);

            if (existingContent != null) {
                existingContent.setTitle(contentRequest.getTitle());
                existingContent.setContent(contentRequest.getContent());

                contentRepository.save(existingContent);

                return ResponseEntity.ok(new MessageResponse("Cập nhật bài thành công!"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Đã xảy ra lỗi khi cập nhật bài."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Đã xảy ra lỗi khi cập nhật bài."));
        }
    }
}