package ppl.momofin.momofinbackend.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ppl.momofin.momofinbackend.service.LoggingService;
import ppl.momofin.momofinbackend.config.SecurityConfig;
import ppl.momofin.momofinbackend.error.InvalidCredentialsException;
import ppl.momofin.momofinbackend.error.OrganizationNotFoundException;
import ppl.momofin.momofinbackend.error.UserAlreadyExistsException;
import ppl.momofin.momofinbackend.model.Organization;
import ppl.momofin.momofinbackend.model.User;
import ppl.momofin.momofinbackend.repository.OrganizationRepository;
import ppl.momofin.momofinbackend.request.RegisterRequest;
import ppl.momofin.momofinbackend.service.UserService;
import ppl.momofin.momofinbackend.request.AuthRequest;
import ppl.momofin.momofinbackend.security.JwtUtil;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private LoggingService loggingService;

    @MockBean
    private OrganizationRepository organizationRepository;

    private User mockUser;
    private ObjectMapper objectMapper;
    private Organization organization;
    private User mockAdmin;
    private static final String VALID_TOKEN = "Bearer validToken";
    private static final String TEST_USERNAME = "testUser";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockUser = new User();
        mockUser.setUserId(1L);
        mockUser.setName("test User real name");
        mockUser.setEmail("test.user@gmail.com");
        mockUser.setPosition("Tester");
        mockUser.setUsername(TEST_USERNAME);
        mockUser.setPassword("testPassword");
        organization = new Organization("Momofin");
        mockUser.setOrganization(organization);

        mockAdmin = new User();
        mockAdmin.setUsername(TEST_USERNAME);
        mockAdmin.setOrganization(organization);

        when(jwtUtil.validateToken("validToken", TEST_USERNAME)).thenReturn(true);
        when(jwtUtil.extractUsername("validToken")).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken("validToken")).thenReturn(true);

        Claims claims = new DefaultClaims();
        claims.put("roles", Collections.singletonList("ROLE_USER"));
        when(jwtUtil.extractAllClaims("validToken")).thenReturn(claims);
    }

    @Test
    void testAuthenticateUserSuccess() throws Exception {
        User loginUser = new User(new Organization("My Organization"), "testUser", "Test User Full Name", "test@example.com", "password", "Tester", false);
        loginUser.setUserId(mockUser.getUserId());
        when(userService.authenticate(anyString(), anyString(), anyString())).thenReturn(loginUser);
        when(jwtUtil.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        AuthRequest authRequest = new AuthRequest();
        authRequest.setOrganizationName("My Organization");
        authRequest.setUsername("testUser");
        authRequest.setPassword("testPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").value("mock-jwt-token"))
                .andExpect(jsonPath("$.user.username").value("testUser"))
                .andExpect(jsonPath("$.user.name").value("Test User Full Name"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.position").value("Tester"));

        verify(loggingService).log(eq(mockUser.getUserId()), eq("INFO"),
                eq("Successful login for user: testUser from organization: My Organization"),
                eq("/auth/login"), anyString());
    }
    @Test
    void testAuthenticateUserInvalidCredentials() throws Exception {
        String wrongPassword = "wrongPassword";
        when(userService.authenticate(anyString(), anyString(), eq(wrongPassword)))
                .thenThrow(new InvalidCredentialsException());

        AuthRequest authRequest = new AuthRequest();
        authRequest.setOrganizationName("My Organization");
        authRequest.setUsername("Hobo Steve Invalid");
        authRequest.setPassword(wrongPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorMessage").value("Your email or password is incorrect"));
        verify(loggingService).log(eq(null), eq("ERROR"),
                eq("Failed login attempt for user: Hobo Steve Invalid from organization: My Organization"),
                eq("/auth/login"), anyString());
    }

    @Test
    void testAuthenticateUserOrganizationNotFound() throws Exception {
        String invalidOrganizationName = "Not Organization";
        when(userService.authenticate(eq(invalidOrganizationName), anyString(), anyString()))
                .thenThrow(new OrganizationNotFoundException(invalidOrganizationName));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setOrganizationName(invalidOrganizationName);
        authRequest.setUsername("test User");
        authRequest.setPassword("testPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorMessage").value("The organization "+ invalidOrganizationName + " is not registered to our database"));
        verify(loggingService).log(eq(null), eq("ERROR"),
                eq("Failed login attempt for user: test User from organization: Not Organization"),
                eq("/auth/login"), anyString());
    }


    @Test
    void testRegisterSuccess() throws Exception {
        when(organizationRepository.findOrganizationByName("Momofin")).thenReturn(Optional.of(organization));
        when(userService.fetchUserByUsername(TEST_USERNAME)).thenReturn(mockAdmin);
        when(userService.registerMember(eq(organization), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName(mockUser.getName());
        registerRequest.setEmail(mockUser.getEmail());
        registerRequest.setPosition(mockUser.getPosition());
        registerRequest.setUsername(mockUser.getUsername());
        registerRequest.setPassword(mockUser.getPassword());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(mockUser.getUsername()))
                .andExpect(jsonPath("$.user.name").value(mockUser.getName()))
                .andExpect(jsonPath("$.user.email").value(mockUser.getEmail()))
                .andExpect(jsonPath("$.user.position").value(mockUser.getPosition()));
        verify(loggingService).log(eq(mockUser.getUserId()), eq("INFO"),
                eq("User: " + mockUser.getUsername() + " with Name: " + mockUser.getName() +
                        " using Email: " + mockUser.getEmail() +
                        " with Position: " + mockUser.getPosition()),
                eq("/auth/register"), anyString());
    }

    @Test
    void testRegisterUserEmailAlreadyInUse() throws Exception {
        String usedEmail = "duplicated.address@gmail.com";
        when(organizationRepository.findOrganizationByName("Momofin")).thenReturn(Optional.of(organization));
        when(userService.fetchUserByUsername(TEST_USERNAME)).thenReturn(mockAdmin);
        when(userService.registerMember(eq(organization), anyString(), anyString(), eq(usedEmail), anyString(), anyString()))
                .thenThrow(new UserAlreadyExistsException("The email "+usedEmail+" is already in use"));

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName("test User real name");
        registerRequest.setEmail(usedEmail);
        registerRequest.setPosition("Tester");
        registerRequest.setUsername("test User");
        registerRequest.setPassword("testPassword");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("The email "+usedEmail+" is already in use"));
    }

    @Test
    void testRegisterUserUsernameAlreadyInUse() throws Exception {
        String usedUsername = "Doppelganger";
        when(organizationRepository.findOrganizationByName("Momofin")).thenReturn(Optional.of(organization));
        when(userService.fetchUserByUsername(TEST_USERNAME)).thenReturn(mockAdmin);
        when(userService.registerMember(eq(organization), eq(usedUsername), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new UserAlreadyExistsException("The username "+usedUsername+" is already in use"));

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName("test User real name");
        registerRequest.setEmail("test.user@gmail.com");
        registerRequest.setPosition("Tester");
        registerRequest.setUsername(usedUsername);
        registerRequest.setPassword("testPassword");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("The username "+usedUsername+" is already in use"));
    }

    @Test
    void testGetAuthenticatedUser() throws Exception {
        when(userService.fetchUserByUsername(TEST_USERNAME)).thenReturn(mockUser);

        mockMvc.perform(get("/auth/info")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(mockUser.getUsername()))
                .andExpect(jsonPath("$.name").value(mockUser.getName()))
                .andExpect(jsonPath("$.email").value(mockUser.getEmail()))
                .andExpect(jsonPath("$.position").value(mockUser.getPosition()));

    }
}
