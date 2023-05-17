package com.assaabloyglobalsolutions.axon.extension.dynamodb.example

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthController {
    @GetMapping()
    fun health(): ResponseEntity<Any> = ResponseEntity.ok().build()
}