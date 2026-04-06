package pl.kathelan.functional.feature6.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple user model used in Feature 6 Stream / Optional exercises.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String name;
    private int age;
    private String email;
}
