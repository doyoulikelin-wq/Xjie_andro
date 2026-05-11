package com.xjie.app.core.network.api

import com.xjie.app.core.model.GenomicsDemoPanel
import com.xjie.app.core.model.MetabolomicsDemoPanel
import com.xjie.app.core.model.MicrobiomeDemoPanel
import com.xjie.app.core.model.OmicsTriadInsight
import com.xjie.app.core.model.ProteomicsDemoPanel
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface OmicsApi {
    @GET("api/omics/demo/metabolomics")
    suspend fun metabolomics(): MetabolomicsDemoPanel

    @GET("api/omics/demo/proteomics")
    suspend fun proteomics(): ProteomicsDemoPanel

    @GET("api/omics/demo/genomics")
    suspend fun genomics(): GenomicsDemoPanel

    @GET("api/omics/demo/microbiome")
    suspend fun microbiome(): MicrobiomeDemoPanel

    @GET("api/omics/demo/triad")
    suspend fun triad(): OmicsTriadInsight

    @Multipart
    @POST("api/omics/metabolomics/upload")
    suspend fun uploadMetabolomics(@Part file: MultipartBody.Part)
}
