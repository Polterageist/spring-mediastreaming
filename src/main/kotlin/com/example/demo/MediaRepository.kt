package com.example.demo

import org.springframework.data.repository.CrudRepository


interface MediaRepository : CrudRepository<MediaModel, Long>
