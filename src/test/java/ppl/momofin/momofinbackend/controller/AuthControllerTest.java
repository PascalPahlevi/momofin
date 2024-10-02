package ppl.momofin.momofinbackend.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

import java.util.Optional;

import static org.mockito.Mockito.*;
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
        mockUser.setName("test User real name");
        mockUser.setEmail("test.user@gmail.com");
        mockUser.setPosition("Tester");
        mockUser.setUsername(TEST_USERNAME);
        mockUser.setPassword("testPassword");
        organization = new Organization("Momofin");

        mockAdmin = new User();
        mockAdmin.setUsername(TEST_USERNAME);
        mockAdmin.setOrganization(organization);
    }

    @Test
    public void testAuthenticateUserSuccess() throws Exception {
        // Mock UserService's authenticate method
        when(userService.authenticate(anyString(), anyString(), anyString())).thenReturn(mockUser);

        // Mock JwtUtil's generateToken method
        when(jwtUtil.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        // Create an authentication request object
        AuthRequest authRequest = new AuthRequest();
        authRequest.setOrganizationName("My Organization");
        authRequest.setUsername("testUser");
        authRequest.setPassword("testPassword");

        // Perform the POST request to /auth/login
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk()) // Assert that the status is 200 OK
                .andExpect(jsonPath("$.jwt").value("mock-jwt-token")); // Assert that the JWT token is in the response
      verify(loggingService).log("INFO", "Successful login for user: testUser from organization: My Organization", "/auth/login");
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
        verify(loggingService).log("ERROR", "Failed login attempt for user: Hobo Steve Invalid from organization: My Organization", "/auth/login");

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
        verify(loggingService).log("ERROR", "Failed login attempt for user: test User from organization: Not Organization", "/auth/login");
    }



    @Test
    void testRegisterUserEmailAlreadyInUse() throws Exception {
        String usedEmail = "duplicated.address@gmail.com";
        when(organizationRepository.findOrganizationByName("Momofin")).thenReturn(Optional.of(organization));
        when(jwtUtil.validateToken("validToken")).thenReturn(true);
        when(jwtUtil.extractUsername("validToken")).thenReturn(TEST_USERNAME);
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
        when(jwtUtil.validateToken("validToken")).thenReturn(true);
        when(jwtUtil.extractUsername("validToken")).thenReturn(TEST_USERNAME);
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

}
