package com.skillrental.service;

import com.skillrental.entity.ProviderProfile;
import com.skillrental.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service @RequiredArgsConstructor
public class MarketplaceService {
    private final ProviderProfileRepository providers;
    private final ProviderEligibilityService eligibility;
    public List<ProviderProfile> search(String q,String skill,BigDecimal min,BigDecimal max,String sort) {
        if(min!=null&&max!=null&&min.compareTo(max)>0)throw new IllegalArgumentException("Khoảng giá không hợp lệ");
        List<ProviderProfile> result=providers.findAll().stream().filter(eligibility::isEligible).toList();
        if(q!=null&&!q.isBlank()) {
            String needle=q.strip().toLowerCase();
            result=result.stream().filter(p->p.getUser().getFullName().toLowerCase().contains(needle)||p.getHeadline().toLowerCase().contains(needle)||(p.getIntroduction()!=null&&p.getIntroduction().toLowerCase().contains(needle))).toList();
        }
        if(skill!=null&&!skill.isBlank()) {
            String needle=skill.strip().toLowerCase();
            result=result.stream().filter(p->p.getSkills().stream().anyMatch(s->s.getName().equalsIgnoreCase(needle))).toList();
        }
        if(min!=null)result=result.stream().filter(p->p.getHourlyRate().compareTo(min)>=0).toList();
        if(max!=null)result=result.stream().filter(p->p.getHourlyRate().compareTo(max)<=0).toList();
        Comparator<ProviderProfile> order=switch(sort==null?"":sort) {
            case "priceAsc"->Comparator.comparing(ProviderProfile::getHourlyRate);
            case "priceDesc"->Comparator.comparing(ProviderProfile::getHourlyRate).reversed();
            default->Comparator.comparing(ProviderProfile::getRating).reversed();
        }
        ;
        return result.stream().sorted(order).toList();
    }
    public List<ProviderProfile> search(String q,String skill,BigDecimal max,String sort) {
        return search(q,skill,null,max,sort);
    }
    public ProviderProfile get(Long id) {
        return providers.findById(id).orElseThrow(()->new IllegalArgumentException("Provider không tồn tại"));
    }
}
