package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.CustomSportRequest;
import com.dongleme.backend.dto.CustomSportView;
import com.dongleme.backend.entity.CustomSport;
import com.dongleme.backend.mapper.CustomSportMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CustomSportService {

    private static final Pattern NAME_RE =
            Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_-]{1,12}$");
    private static final int MAX_SPORTS = 20;

    private final CustomSportMapper sportMapper;
    private final AuthService authService;

    public CustomSportService(CustomSportMapper sportMapper, AuthService authService) {
        this.sportMapper = sportMapper;
        this.authService = authService;
    }

    public List<CustomSportView> list(String userId) {
        authService.requireUser(userId);
        return sportMapper.selectList(
                        Wrappers.<CustomSport>lambdaQuery()
                                .eq(CustomSport::getUserId, userId)
                                .orderByAsc(CustomSport::getCreatedAt)
                                .orderByAsc(CustomSport::getId))
                .stream()
                .map(CustomSportView::of)
                .toList();
    }

    public CustomSportView create(String userId, CustomSportRequest request) {
        authService.requireUser(userId);

        String name = requireName(request.name());
        double met = requireMet(request.met());
        if (Sports.BUILTIN_MET.containsKey(name)) {
            throw ApiException.conflict("该运动名称已存在");
        }

        long count = sportMapper.selectCount(
                Wrappers.<CustomSport>lambdaQuery().eq(CustomSport::getUserId, userId));
        if (count >= MAX_SPORTS) {
            throw ApiException.badRequest("最多添加 " + MAX_SPORTS + " 个自定义运动");
        }

        CustomSport sport = new CustomSport();
        sport.setUserId(userId);
        sport.setName(name);
        sport.setMet(BigDecimal.valueOf(met).setScale(1, RoundingMode.HALF_UP));

        try {
            sportMapper.insert(sport);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("你已经添加过这个运动");
        }

        return CustomSportView.of(sportMapper.selectById(sport.getId()));
    }

    public CustomSportView update(String userId, CustomSportRequest request) {
        authService.requireUser(userId);
        if (request.id() == null) {
            throw ApiException.badRequest("缺少 id");
        }

        String name = requireName(request.name());
        double met = requireMet(request.met());

        CustomSport sport = sportMapper.selectOne(
                Wrappers.<CustomSport>lambdaQuery()
                        .eq(CustomSport::getUserId, userId)
                        .eq(CustomSport::getId, request.id())
                        .last("limit 1"));
        if (sport == null) {
            throw ApiException.notFound("自定义运动不存在");
        }

        if (!sport.getName().equals(name)) {
            if (Sports.BUILTIN_MET.containsKey(name)) {
                throw ApiException.conflict("该运动名称已存在");
            }
            Long duplicateCount = sportMapper.selectCount(
                    Wrappers.<CustomSport>lambdaQuery()
                            .eq(CustomSport::getUserId, userId)
                            .eq(CustomSport::getName, name)
                            .ne(CustomSport::getId, request.id()));
            if (duplicateCount != null && duplicateCount > 0) {
                throw ApiException.conflict("你已经添加过这个运动");
            }
            sport.setName(name);
        }
        sport.setMet(BigDecimal.valueOf(met).setScale(1, RoundingMode.HALF_UP));

        try {
            sportMapper.updateById(sport);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("你已经添加过这个运动");
        }

        return CustomSportView.of(sportMapper.selectById(sport.getId()));
    }

    public void delete(String userId, Long id) {
        authService.requireUser(userId);
        if (id == null) {
            throw ApiException.badRequest("缺少 id");
        }

        int affected = sportMapper.delete(
                Wrappers.<CustomSport>lambdaQuery()
                        .eq(CustomSport::getUserId, userId)
                        .eq(CustomSport::getId, id));
        if (affected == 0) {
            throw ApiException.notFound("自定义运动不存在");
        }
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!NAME_RE.matcher(name).matches()) {
            throw ApiException.badRequest("运动名称需为 1–12 位中英文、数字、下划线或连字符");
        }
        return name;
    }

    private static double requireMet(Double met) {
        if (met == null || !Double.isFinite(met) || met < 1 || met > 20) {
            throw ApiException.badRequest("MET 需在 1–20 之间");
        }
        return met;
    }
}
