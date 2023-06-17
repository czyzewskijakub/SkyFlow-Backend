package pl.ioad.skyflow.logic.user;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pl.ioad.skyflow.database.model.User;
import pl.ioad.skyflow.database.repository.UserRepository;
import pl.ioad.skyflow.logic.exception.type.*;
import pl.ioad.skyflow.logic.user.dto.Mapper;
import pl.ioad.skyflow.logic.user.payload.request.LoginRequest;
import pl.ioad.skyflow.logic.user.payload.request.UpdateDataRequest;
import pl.ioad.skyflow.logic.user.payload.request.UserDataRequest;
import pl.ioad.skyflow.logic.user.payload.response.AuthorizationResponse;
import pl.ioad.skyflow.logic.user.payload.response.UserResponse;
import pl.ioad.skyflow.logic.user.security.jwt.JwtUtils;


import java.util.Optional;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder encoder;
    private final Mapper mapper = new Mapper();

    public UserResponse register(UserDataRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ForbiddenException("Email is taken");
        }

        User user = userRepository.save(User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(encoder.encode(request.getPassword()))
                .profilePictureUrl(request.getPictureUrl())
                .isAdmin(false)
                .build());
        return new UserResponse(
                HttpStatus.OK.value(),
                "Successfully registered user account",
                mapper.mapUser(user));

    }

    public UserResponse registerAdmin(UserDataRequest request, HttpServletRequest http) {
        String token = http.getHeader("Authorization");
        if (token == null) {
            throw new ForbiddenException("You are not authorized");
        }
        token = token.substring("Bearer ".length());
        var user = userRepository.findByEmail(jwtUtils.extractUsername(token));
        if (user.isPresent() && !user.get().getIsAdmin()) {
            throw new InvalidBusinessArgumentException("You cannot register new admin as standard user");
        } else if (!request.getEmail().contains("@") && !request.getEmail().contains(".")) {
            throw new InvalidDataException("Wrong register input");
        } else if (userRepository.existsByEmail(request.getEmail())) {
            throw new ForbiddenException("Email is taken");
        }

        User newUser = userRepository.save(User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(encoder.encode(request.getPassword()))
                .profilePictureUrl(request.getPictureUrl())
                .isAdmin(true)
                .build());
        return new UserResponse(
                HttpStatus.OK.value(),
                "Successfully registered admin user account",
                mapper.mapUser(newUser));
    }

    public AuthorizationResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        if (!userRepository.existsByEmail(request.email())) {
            throw new EntityNotFoundException("User with given data does not exist");
        }
        if (httpServletRequest.getHeader(AUTHORIZATION) != null) {
            throw new AuthException("You cannot log in while you are logged in");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(), request.password()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtUtils.generateJwtToken(authentication);
        return new AuthorizationResponse(HttpStatus.OK.value(), "Successfully logged in", token);
    }

    public UserResponse update(Long userId, UpdateDataRequest userData) {
        Optional<User> existingUser = userRepository.findById(userId);
        if (existingUser.isEmpty()) {
            throw new EntityNotFoundException("User not found");
        }
        if (userRepository.existsByEmail(userData.getEmail())) {
            throw new DuplicatedDataException("User with given email already exists");
        }

        if (userData.getFirstName() != null && !userData.getFirstName().isEmpty()) {
            existingUser.get().setFirstName(userData.getFirstName());
        }
        if (userData.getLastName() != null && !userData.getLastName().isEmpty()) {
            existingUser.get().setLastName(userData.getLastName());
        }
        if (userData.getEmail() != null && !userData.getEmail().isEmpty()) {
            existingUser.get().setEmail(userData.getEmail());
        }
        if (userData.getPassword() != null && !userData.getPassword().isEmpty()) {
            existingUser.get().setPasswordHash(encoder.encode(userData.getPassword()));
        }
        if (userData.getPictureUrl() != null && !userData.getPictureUrl().isEmpty()) {
            existingUser.get().setProfilePictureUrl(userData.getPictureUrl());
        }

        userRepository.save(existingUser.get());

        return new UserResponse(HttpStatus.ACCEPTED.value(),
                "User data updated",
                        mapper.mapUser(existingUser.get())
                );
    }


}
