package common.config;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class BookkeeperConfig {

    int bufferSize;
}
