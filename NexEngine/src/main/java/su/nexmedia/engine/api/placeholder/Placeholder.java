package su.nexmedia.engine.api.placeholder;

import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;

public interface Placeholder {

    @NotNull
    PlaceholderMap getPlaceholders();

    @NotNull
    default UnaryOperator<String> replacePlaceholders() {
        return this.getPlaceholders().replacer();
    }
}
