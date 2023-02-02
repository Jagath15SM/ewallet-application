package com.project.majorproject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController
{

    @Autowired
    UserService userService;

    @PostMapping("/add")
    String createUser(@RequestBody() UserRequestDto userRequestDto){
        return userService.addUser(userRequestDto);
    }

    @GetMapping("/findByUser/{name}")
    User getUserByUserName(@PathVariable("name") String userName){

        return userService.findUserByUserName(userName);
    }
}
