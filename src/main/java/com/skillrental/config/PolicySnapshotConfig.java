package com.skillrental.config;
import com.skillrental.service.PolicyConsentService;import org.springframework.context.annotation.*;
@Configuration public class PolicySnapshotConfig {
 @Bean org.springframework.boot.CommandLineRunner policySnapshots(PolicyConsentService service){return args->{for(String type:PolicyConsentService.VERSIONS.keySet())service.snapshot(type);};}
}
