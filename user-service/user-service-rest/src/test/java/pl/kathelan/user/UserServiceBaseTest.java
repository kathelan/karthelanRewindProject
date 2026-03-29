package pl.kathelan.user;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kathelan.common.test.BaseIntegrationTest;
import pl.kathelan.user.service.UserService;

public abstract class UserServiceBaseTest extends BaseIntegrationTest {

    @MockitoBean
    protected UserService userService;
}
