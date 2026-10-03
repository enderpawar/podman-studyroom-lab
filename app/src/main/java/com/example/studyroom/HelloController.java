package com.example.studyroom.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity; // status response를 반환하는 친구 같은데..
import org.springframework.http.HttpStatus;

@RestController // Spring이 관리하는 어노테이션으로 등록
public class HelloController{
    @GetMapping("/hello") // HTTP GET 요청으로 /hello 요청이 왔을 시 해준다.
    public String hello(){ // String은 무조건 문자열로 써야한다.
        return "Hello,StudyRoom!";
    }

    @GetMapping("/health") //서버 상태 확인하는 것 같은데.
    public ResponseEntity<String> health(){
        return ResponseEntity
                .status(HttpStatus.OK) // 이 상태코드를 내가 골랐다는 것이 드러남. 200을 고르게 됨.
                .body("OK");
    }
    @GetMapping("/bye") //
    public ResponseEntity<String> bye(){ // 상태 기본값을 다르게 명시할때는 ResponseEntity를 통해 만들어야한다.
        return ResponseEntity
                .status(201)
                .body("Created");
    }
}



/*
@RestController
public class HelloController{
    @GetMapping("/hello")
    public string hello(){
        return "Hello,StudyRoom!";
    }
 */

// 상태코드 200이 찍히지 않을까..응답이 정상 반환 됐잖아?

/*
Q1.HelloController의 hello() 메서드 어디에도 상태코드를 명시하지 않았는데,

 왜 200이 나왔을까요? 컴퓨터 네트워크에서 배운 400(Bad Request)·404(Not Found)와 비교해서,
 200이 속한 상태코드 "계열"이 뭔지부터 떠올려서 한번 설명해보세요.
 (정답 대조는 답변 주시면 바로 해드릴게요 — 지금은 직접 인출이 먼저입니다.)

A1 - 모른다고 답했음. DTO와 연관있지 않을까도 생각했는데.. 답은

정답: 컨트롤러 메서드가 예외없이 정상 반환하면 이상신호 없음 - 성공으로 간주해 기본값 200을 붙인대.
400/404가 나오려면 명시적으로 신호가 필요함.

문득 든 질문.

@GetMapping("/hello") // HTTP GET 요청으로 /hello 요청이 왔을 시 해준다.

public String hello(){ // String은 무조건 문자열로 써야한다.

return "Hello,StudyRoom!";

}

근데 이런식으로 REST API 호출 경로 이름과 메서드 이름은 통일시켜야하나?

-> 아니다. REST API의 호출경로 / 자바 메서드의 이름은 목적이 아예 다르다.

왜 통일하지 않아도 될까요?
URL 경로 (@GetMapping("/hello")):

외부(클라이언트)에 공개되는 주소입니다.

브라우저나 앱이 어떤 자원(Resource)을 요청할지 지정하는 '문대문' 역할을 합니다.

메서드 이름 (public String hello()):

내부(자바 코드)에서 개발자가 구분하기 위해 쓰는 이름입니다.

이 메서드가 내부적으로 어떤 일을 수행하는지 설명하는 '기능 명칭' 역할을 합니다.

💡 실무 관례 & 이름 짓기 팁
URL 경로: 보통 소문자, 하이픈(-), 명사 위주로 작성합니다. (예: /user-profiles)

자바 메서드 이름: 동사 + 명사 조합의 카멜케이스(CamelCase)로 어떤 역할을 하는지 명확히 작성합니다.
(예: getUserProfiles(), createOrder())
*/