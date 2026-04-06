package com.xinyu.ecommerce.user.controller;

import com.xinyu.ecommerce.common.result.Result;
import com.xinyu.ecommerce.user.entity.User;
import com.xinyu.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public Result<Map<String, Long>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String email = request.get("email");
        String phone = request.get("phone");
        User user = userService.register(username, email, phone);
        Map<String, Long> data = new HashMap<>();
        data.put("userId", user.getId());
        return Result.success(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        User user = userService.login(username);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        return Result.success(data);
    }

    @GetMapping("/info")
    public Result<User> getUserInfo(@RequestParam Long userId) {
        User user = userService.getUserInfo(userId);
        return Result.success(user);
    }

    @PutMapping("/update")
    public Result<Boolean> updateUser(@RequestBody Map<String, String> request) {
        Long userId = Long.parseLong(request.get("userId"));
        String email = request.get("email");
        String phone = request.get("phone");
        boolean success = userService.updateUser(userId, email, phone);
        return Result.success(success);
    }
}
