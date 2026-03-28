package pl.kathelan.auth.sse;

import pl.kathelan.auth.api.dto.ProcessState;

import java.util.List;

public record ProcessStreamEvent(ProcessState state, List<String> allowedActions) {

    public static ProcessStreamEvent of(ProcessState state) {
        List<String> actions = state == ProcessState.PENDING ? List.of("CANCEL") : List.of();
        return new ProcessStreamEvent(state, actions);
    }
}