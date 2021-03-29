package com.hmobility.parkingfriends.admin.controller.agency

import com.google.gson.Gson
import com.hmobility.parkingfriends.admin.constants.AdminConstants
import com.hmobility.parkingfriends.admin.controller.BaseController
import com.hmobility.parkingfriends.admin.entity.ResultEntity
import com.hmobility.parkingfriends.commonmodel.entity.projection.agency.*
import com.hmobility.parkingfriends.commonmodel.entity.projection.parking.AdminParkingLotSearchRequest
import com.hmobility.parkingfriends.commonmodel.entity.projection.parking.AdminParkingLotSearchResponse
import com.hmobility.parkingfriends.commonmodel.type.parking.ParkingItemType
import com.hmobility.parkingfriends.commonmodel.utils.DateUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.Valid
import org.springframework.web.bind.annotation.ModelAttribute
import software.amazon.ion.impl.PrivateUtils
import java.util.*


@Controller
@RequestMapping("/agency")
class AgencyController(private val restTemplate: RestTemplate, private val gson: Gson): BaseController() {

@Value("\${api.parkingfriends.uri}")
    val parkingFriendsApiUri: String = ""

@Value("\${api.parkingfriends.version}")
    val parkingFriendsVersion: String = ""

/**
 * 결제대행 등록 화면
 */
@GetMapping("/create")
    fun insertAgencyView(model:Model): String{

            model.addAttribute("agencyOrderEntity", AgencyOrderEntity())

            return "agency/create"
            }

@PostMapping("/parkinglots/search")
@ResponseBody
    fun getParkingLot(@RequestBody adminParkingLotSearchRequest: AdminParkingLotSearchRequest, mav: ModelAndView): ModelAndView{
        mav.viewName = "jsonView"

        val url = "${parkingFriendsApiUri}${parkingFriendsVersion}${AdminConstants.Companion.ApiUrl.ADMIN_PARKINGLOT_SEARCH.apiUrl}"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val acceptCharset = arrayListOf<Charset>()
        acceptCharset.add(StandardCharsets.UTF_8)
        headers.acceptCharset = acceptCharset

        var result = ResultEntity<AdminParkingLotSearchResponse>()

        try {
        val request = HttpEntity(adminParkingLotSearchRequest, headers)
        val response = restTemplate.exchange(url, HttpMethod.POST , request,
        object : ParameterizedTypeReference<ResultEntity<AdminParkingLotSearchResponse>>() {

        })

        if (response.statusCode == HttpStatus.OK) {
        result = response.body!!
        mav.addObject("result", true)
        mav.addObject("resultData", result.data.parkingLotList)
        //mav.addObject("resultDataSize", result.data.parkingLotList?.size)
        } else {
        mav.addObject("result", false)
        }

        } catch (e: Exception) {
        log.error("Exception, during search parkingLots, message=${e.message}", e)
        mav.addObject("result", false)
        }

        return mav
        }

@PostMapping("/amount")
@ResponseBody
    fun getPaymentAmount(@RequestBody agencyAmountEntity: AgencyAmountEntity, mav: ModelAndView): ModelAndView{
        mav.viewName = "jsonView"

        val url = "${parkingFriendsApiUri}${parkingFriendsVersion}${AdminConstants.Companion.ApiUrl.ADMIN_AGENCY_AMOUNT.apiUrl}"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val acceptCharset = arrayListOf<Charset>()
        acceptCharset.add(StandardCharsets.UTF_8)
        headers.acceptCharset = acceptCharset

        if(agencyAmountEntity.fromDate == null) {
        agencyAmountEntity.fromDate = DateUtils.convertString(LocalDate.now(), DateUtils.DATE_FORMAT_DEFAULT)
        }

        var params = hashMapOf<String, Any?>()

        params["type"] = agencyAmountEntity.type
        params["parkingLotId"] = agencyAmountEntity.parkingLotId
        when(agencyAmountEntity.type){
        ParkingItemType.TIME -> {
        if(agencyAmountEntity.fromTime == null) {
        agencyAmountEntity.fromTime = DateUtils.convertString(LocalTime.now(), DateUtils.TIME_FORMAT_DEFAULT)
        }
        if(agencyAmountEntity.reserveDiff == null) {
        agencyAmountEntity.reserveDiff = 0
        }
        var from = agencyAmountEntity.fromDate!!.replace("-","") + agencyAmountEntity.fromTime!!.replace(":","") + "00"
        var to = DateUtils.convertString(DateUtils.convertLocalDateTime(from).plusMinutes(agencyAmountEntity.reserveDiff!!.toLong()), DateUtils.DATETIME_FORMAT_DEFAULT);
        params["from"] = from
        params["to"] = to
        if("EXTENSION" == agencyAmountEntity.reserveType) {
        params["extension"] = true
        }
        }
        ParkingItemType.FIXED -> params["productId"] = agencyAmountEntity.productId
        ParkingItemType.MONTHLY -> {
        var from = agencyAmountEntity.fromDate!!.replace("-","") + "000000"
        var to =  DateUtils.convertString(DateUtils.convertLocalDateTime(from).plusDays(agencyAmountEntity.monthlyCount!!*31.toLong()), DateUtils.DATETIME_FORMAT_DEFAULT);
        params["productId"] = agencyAmountEntity.productId
        params["from"] = from
        params["to"] = to
        }

        }

        var result = ResultEntity<AgencyAmountResponse>()

        try {
        val request = HttpEntity(params, headers)
        val response = restTemplate.postForEntity(url, request, String::class.java)

        if (response.statusCode == HttpStatus.OK) {
        result = gson.fromJson(response.body, ResultEntity<AgencyAmountResponse>()::class.java)
        if(result.code == "0000") {
        mav.addObject("result", true)
        mav.addObject("resultData", result.data)
        } else {
        mav.addObject("result", false)
        // message 한글 UTF-8 변환
        val msg = String((result.message).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        mav.addObject("message", msg)
        }
        } else {
        mav.addObject("result", false)
        mav.addObject("message", "")
        }

        } catch (e: Exception) {
        log.error("Exception, during get amount, message=${e.message}", e)
        mav.addObject("result", false)
        mav.addObject("message", e.message)
        }

        return mav
        }

@PostMapping("/order")
    fun orderAgency(@Valid @RequestBody agencyOrderEntity : AgencyOrderEntity?,
        bindingResult: BindingResult,
        mav: ModelAndView
        ): ModelAndView{
//        var mav = ModelAndView()
        mav.viewName = "jsonView"
        var resultMsg : String = "결제대행 성공했습니다."

        if (bindingResult.hasErrors()) {
        log.debug("##### has Error, {}", bindingResult)
//            model.addAttribute("agencyOrderEntity", agencyOrderEntity)
        mav.addObject("agencyOrderEntity", agencyOrderEntity)
//            mav.addObject("agencyOrderEntity", agencyOrderEntity)
//            redirectAttributes.addFlashAttribute("agencyOrderEntity", bindingResult);
//            redirectAttributes.addFlashAttribute("agencyOrderEntity", agencyOrderEntity);
//            redirectAttributes.addFlashAttribute("agencyOrderEntity", agencyOrderEntity)
//            mav.viewName = "redirect:/agency/create"
//            return ModelAndView("redirect:/agency/create")

//            var validStr = ""
//            for(i in 1..bindingResult.errorCount) {
//                validStr += bindingResult.allErrors[i-1].defaultMessage
//                validStr += "/n"
//            }

        mav.addObject("result", false)
        mav.addObject("resultMsg", bindingResult.allErrors[0].defaultMessage)
//            mav.addObject("resultMsg", validStr)

        return mav
        }

        val url = "${parkingFriendsApiUri}${parkingFriendsVersion}${AdminConstants.Companion.ApiUrl.ADMIN_AGENCY_ORDER.apiUrl}"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val acceptCharset = arrayListOf<Charset>()
        acceptCharset.add(StandardCharsets.UTF_8)
        headers.acceptCharset = acceptCharset

        var params = hashMapOf<String, Any?>()

        params["parkingItemType"] = agencyOrderEntity!!.type
        params["parkingLotId"] = agencyOrderEntity.parkingLotId
        params["totalAmount"] = agencyOrderEntity.amount
        params["paymentAmount"] = agencyOrderEntity.amount

        var orderCar = OrderCar()
        orderCar.number = agencyOrderEntity.carNo
        orderCar.phoneNumber = agencyOrderEntity.phoneNumber
        params["car"] = orderCar

        var cardEntity = CardEntity()
        cardEntity.buyerName = agencyOrderEntity.buyerName
        cardEntity.buyerAuthNum = agencyOrderEntity.buyerAuthNum
        cardEntity.cardNum = agencyOrderEntity.cardNum
        cardEntity.cardExpire = agencyOrderEntity.cardExpire
        cardEntity.cardPwd = agencyOrderEntity.cardPwd
        params["card"] = cardEntity

        when(agencyOrderEntity.type){
        ParkingItemType.TIME -> {
        if(agencyOrderEntity.fromTime == null) {
        agencyOrderEntity.fromTime = DateUtils.convertString(LocalTime.now(), DateUtils.TIME_FORMAT_DEFAULT)
        }
        if(agencyOrderEntity.reserveDiff == null) {
        agencyOrderEntity.reserveDiff = 0
        }
        var from = agencyOrderEntity.fromDate!!.replace("-","") + agencyOrderEntity.fromTime!!.replace(":","") + "00"
        var to = DateUtils.convertString(DateUtils.convertLocalDateTime(from).plusMinutes(agencyOrderEntity.reserveDiff!!.toLong()), DateUtils.DATETIME_FORMAT_DEFAULT);
        params["from"] = from
        params["to"] = to
        if("EXTENSION" == agencyOrderEntity.reserveType) {
        params["extension"] = true
        }

        params["originAmount"] = agencyOrderEntity.originAmount
        }

        ParkingItemType.FIXED -> {
        params["itemId"] = agencyOrderEntity.productId
        }

        ParkingItemType.MONTHLY -> {
        var from = agencyOrderEntity.fromDate!!.replace("-","") + "000000"
        var to =  DateUtils.convertString(DateUtils.convertLocalDateTime(from).plusDays(agencyOrderEntity.monthlyCount!!*32.toLong()), DateUtils.DATETIME_FORMAT_DEFAULT);
        params["itemId"] = agencyOrderEntity.productId
        params["from"] = from
        params["to"] = to
        }

        }

        var result = ResultEntity<AgencyOrderResponse>()

        try {
        val request = HttpEntity(params, headers)
        val response = restTemplate.exchange(url, HttpMethod.POST, request,
        object : ParameterizedTypeReference<ResultEntity<AgencyOrderResponse>>() {

        })

        if (response.statusCode == HttpStatus.OK) {
        result = response.body!!

        mav.addObject("result", result.data.resultstatus)
        mav.addObject("resultMsg", result.data.resultMessage)
        } else {
        mav.addObject("result", false)
        mav.addObject("resultMsg", response.body!!.data.resultMessage)
        }

        } catch (e: Exception) {
        log.error("Exception, during order, message=${e.message}", e)
        mav.addObject("result", false)
        mav.addObject("resultMsg", "결제 대행에 실패했습니다.")
        }

        return mav
        }

        }