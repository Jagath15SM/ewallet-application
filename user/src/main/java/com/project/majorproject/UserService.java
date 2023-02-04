package com.project.majorproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class UserService
{
    @Autowired
    RedisTemplate<String, User> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    public String addUser(UserRequestDto userRequestDto)
    {
        // Create User object
        User user = User.builder().userName(userRequestDto
                .getUserName()).age(userRequestDto.getAge()).name(userRequestDto.getName())
                .email(userRequestDto.getEmail()).mobNo(userRequestDto.getMobNo()).build();

        //Save it to the db
        userRepository.save(user);
        //Save it in the cache
        saveInCache(user);

        //Send an update to the wallet module/ wallet service
        // ---> that create a new wallet from the userName sent as a string.
        System.out.println("Sending user info via kafka : "+user.getUserName()+ user.getAge()+user.getEmail());
        kafkaTemplate.send("create_wallet",user.getUserName());
        return "User Added successfully";
    }

    public void saveInCache(User user){

        Map map = objectMapper.convertValue(user,Map.class);

        String key = "USER_KEY"+user.getUserName();
        System.out.println("The user key:"+key);
        redisTemplate.opsForHash().putAll(key,map);
        redisTemplate.expire(key, Duration.ofHours(48));
    }

    public User findUserByUserName(String userName){

        //1. find in the redis cache
        Map map = redisTemplate.opsForHash().entries(userName);
        User user;
        //If not found in the redis/map
        if(map==null){
            //Find the userObject from the userRepo
            user = userRepository.findByUserName(userName);
            //Save that found user in the cache
            saveInCache(user);
            return user;
        }else{
            //We found out the User object
            user = objectMapper.convertValue(map, User.class);
            return user;
        }
    }

    public UserResponseDto getEmailName(String UserName){
        User user = userRepository.findByUserName(UserName);
        UserResponseDto userResponseDto = UserResponseDto.builder()
                .name(user.getName()).email(user.getEmail()).build();
        return userResponseDto;
    }





}
