package io.mosip.pmp.regdevice.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.kernel.core.util.EmptyCheckUtils;
import io.mosip.pmp.authdevice.constants.DeviceDetailExceptionsConstant;
import io.mosip.pmp.authdevice.dto.ColumnCodeValue;
import io.mosip.pmp.authdevice.dto.DeviceDetailDto;
import io.mosip.pmp.authdevice.dto.DeviceDetailSearchResponseDto;
import io.mosip.pmp.authdevice.dto.DeviceDetailUpdateDto;
import io.mosip.pmp.authdevice.dto.DeviceSearchDto;
import io.mosip.pmp.authdevice.dto.FilterResponseCodeDto;
import io.mosip.pmp.authdevice.dto.DeviceDetailSearchDto;
import io.mosip.pmp.authdevice.dto.IdDto;
import io.mosip.pmp.authdevice.dto.RegistrationSubTypeDto;
import io.mosip.pmp.authdevice.dto.UpdateDeviceDetailStatusDto;
import io.mosip.pmp.authdevice.exception.RequestException;
import io.mosip.pmp.authdevice.util.AuditUtil;
import io.mosip.pmp.authdevice.util.AuthDeviceConstant;
import io.mosip.pmp.common.dto.DeviceFilterValueDto;
import io.mosip.pmp.common.dto.FilterData;
import io.mosip.pmp.common.dto.FilterDto;
import io.mosip.pmp.common.dto.PageResponseDto;
import io.mosip.pmp.common.helper.FilterHelper;
import io.mosip.pmp.common.helper.SearchHelper;
import io.mosip.pmp.common.util.MapperUtils;
import io.mosip.pmp.common.util.PageUtils;
import io.mosip.pmp.common.validator.FilterColumnValidator;
import io.mosip.pmp.partner.entity.Partner;
import io.mosip.pmp.partner.repository.PartnerServiceRepository;
import io.mosip.pmp.partner.util.PartnerUtil;
import io.mosip.pmp.common.dto.SearchFilter;
import io.mosip.pmp.regdevice.entity.RegDeviceDetail;
import io.mosip.pmp.regdevice.entity.RegRegistrationDeviceSubType;
import io.mosip.pmp.regdevice.entity.RegRegistrationDeviceType;
import io.mosip.pmp.regdevice.repository.RegDeviceDetailRepository;
import io.mosip.pmp.regdevice.repository.RegRegistrationDeviceSubTypeRepository;
import io.mosip.pmp.regdevice.service.RegDeviceDetailService;

@Service
@Transactional
public class RegDeviceDetailServiceImpl implements RegDeviceDetailService {

	private static final String Pending_Approval = "Pending_Approval";
	private static final String ALL = "all";

	@Autowired
	FilterColumnValidator filterColumnValidator;

	@Autowired
	FilterHelper filterHelper;

	@Autowired
	AuditUtil auditUtil;

	@Autowired
	RegDeviceDetailRepository deviceDetailRepository;

	@Autowired
	RegRegistrationDeviceSubTypeRepository registrationDeviceSubTypeRepository;

	@Autowired
	PartnerServiceRepository partnerRepository;

	@Autowired
	SearchHelper searchHelper;

	@Autowired
	private PageUtils pageUtils;

	@Override
	public IdDto createDeviceDetails(DeviceDetailDto deviceDetailDto) {
		RegDeviceDetail entity = new RegDeviceDetail();
		RegDeviceDetail deviceDetail = null;
		IdDto dto = new IdDto();

		RegRegistrationDeviceSubType registrationDeviceSubType = registrationDeviceSubTypeRepository
				.findByCodeAndTypeCodeAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(
						deviceDetailDto.getDeviceSubTypeCode(), deviceDetailDto.getDeviceTypeCode());
		if (registrationDeviceSubType == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_CREATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorMessage()),
					"AUT-002");
			throw new RequestException(DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorCode(),
					DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorMessage());
		} else {
			entity.setDeviceSubTypeCode(registrationDeviceSubType.getCode());
			entity.setDeviceTypeCode(registrationDeviceSubType.getDeviceTypeCode());
		}
		Partner partner =  partnerRepository.findByIdAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(
				deviceDetailDto.getDeviceProviderId());
		if (partner == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_CREATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorMessage()),
					"AUT-003");
			throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorCode(),
					DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorMessage());
		}
		deviceDetailDto.setPartnerOrganizationName(partner.getName());
		if (deviceDetailRepository.findByDeviceDetail(deviceDetailDto.getMake(), deviceDetailDto.getModel(),
				deviceDetailDto.getDeviceProviderId(), deviceDetailDto.getDeviceSubTypeCode(),
				deviceDetailDto.getDeviceTypeCode()) != null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_CREATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_EXIST.getErrorCode(),
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_EXIST.getErrorMessage()),
					"AUT-004");
			throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_DETAIL_EXIST.getErrorCode(),
					DeviceDetailExceptionsConstant.DEVICE_DETAIL_EXIST.getErrorMessage());
		}
		entity = getCreateMapping(entity, deviceDetailDto);
		deviceDetail = deviceDetailRepository.save(entity);
		dto.setId(deviceDetail.getId());
		return dto;
	}

	private RegDeviceDetail getCreateMapping(RegDeviceDetail deviceDetail, DeviceDetailDto deviceDetailDto) {
		deviceDetail.setId(deviceDetailDto.getId() == null ? PartnerUtil.generateId(): deviceDetailDto.getId());
		deviceDetail.setIsActive(false);
		deviceDetail.setApprovalStatus(Pending_Approval);
		Authentication authN = SecurityContextHolder.getContext().getAuthentication();
		if (!EmptyCheckUtils.isNullEmpty(authN)) {
			deviceDetail.setCrBy(authN.getName());
		}
		deviceDetail.setCrDtimes(LocalDateTime.now(ZoneId.of("UTC")));
		deviceDetail.setDeviceProviderId(deviceDetailDto.getDeviceProviderId());
		deviceDetail.setMake(deviceDetailDto.getMake());
		deviceDetail.setModel(deviceDetailDto.getModel());
		deviceDetail.setPartnerOrganizationName(deviceDetailDto.getPartnerOrganizationName());
		return deviceDetail;

	}

	@Override
	public IdDto updateDeviceDetails(DeviceDetailUpdateDto deviceDetailDto) {
		RegDeviceDetail entity = new RegDeviceDetail();
		RegDeviceDetail deviceDetail = null;
		IdDto dto = new IdDto();
		entity = deviceDetailRepository.findByIdAndIsDeletedFalseOrIsDeletedIsNull(deviceDetailDto.getId());
		if (entity == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_UPDATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorMessage()),
					"AUT-008");
			throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorCode(), String
					.format(DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorMessage(), dto.getId()));
		}
		RegRegistrationDeviceSubType registrationDeviceSubType = registrationDeviceSubTypeRepository
				.findByCodeAndTypeCodeAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(
						deviceDetailDto.getDeviceSubTypeCode(), deviceDetailDto.getDeviceTypeCode());
		if (registrationDeviceSubType == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_UPDATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorMessage()),
					"AUT-009");
			throw new RequestException(DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorCode(),
					DeviceDetailExceptionsConstant.REG_DEVICE_SUB_TYPE_NOT_FOUND.getErrorMessage());
		} else {
			entity.setDeviceSubTypeCode(registrationDeviceSubType.getCode());
			entity.setDeviceTypeCode(registrationDeviceSubType.getDeviceTypeCode());
		}
		if ((partnerRepository.findByIdAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(
				deviceDetailDto.getDeviceProviderId())) == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_UPDATE, RegDeviceDetail.class.getCanonicalName()),
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorMessage()),
					"AUT-010");
			throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorCode(),
					DeviceDetailExceptionsConstant.DEVICE_PROVIDER_NOT_FOUND.getErrorMessage());
		}

		entity = getUpdateMapping(entity, deviceDetailDto);
		deviceDetail = deviceDetailRepository.save(entity);
		dto.setId(deviceDetail.getId());
		return dto;
	}

	private RegDeviceDetail getUpdateMapping(RegDeviceDetail deviceDetail, DeviceDetailUpdateDto deviceDetailDto) {
		deviceDetail.setId(deviceDetailDto.getId());
		Authentication authN = SecurityContextHolder.getContext().getAuthentication();
		if (!EmptyCheckUtils.isNullEmpty(authN)) {
			deviceDetail.setUpdBy(authN.getName());
		}
		deviceDetail.setUpdDtimes(LocalDateTime.now(ZoneId.of("UTC")));
		deviceDetail.setDeviceProviderId(deviceDetailDto.getDeviceProviderId());
		deviceDetail.setMake(deviceDetailDto.getMake());
		deviceDetail.setModel(deviceDetailDto.getModel());
		deviceDetail.setPartnerOrganizationName(deviceDetailDto.getPartnerOrganizationName());
		return deviceDetail;

	}

	@Override
	public String updateDeviceDetailStatus(UpdateDeviceDetailStatusDto deviceDetails) {
		RegDeviceDetail entity = deviceDetailRepository
				.findByIdAndIsDeletedFalseOrIsDeletedIsNull(deviceDetails.getId());
		if (entity == null) {
			auditUtil.auditRequest(
					String.format(AuthDeviceConstant.FAILURE_UPDATE, RegDeviceDetail.class.getCanonicalName()),					
					AuthDeviceConstant.AUDIT_SYSTEM,
					String.format(AuthDeviceConstant.FAILURE_DESC,
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorCode(),
							DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorMessage()),
					"AUT-008");
			throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorCode(),
					String.format(DeviceDetailExceptionsConstant.DEVICE_DETAIL_NOT_FOUND.getErrorMessage(),
							deviceDetails.getId()));
		}
		Authentication authN = SecurityContextHolder.getContext().getAuthentication();
		if (!EmptyCheckUtils.isNullEmpty(authN)) {
			entity.setUpdBy(authN.getName());
			entity.setUpdDtimes(LocalDateTime.now(ZoneId.of("UTC")));
		}

		if (deviceDetails.getApprovalStatus().equals(AuthDeviceConstant.APPROVE)) {
			entity.setApprovalStatus(AuthDeviceConstant.APPROVED);
			entity.setIsActive(true);
			deviceDetailRepository.save(entity);
			return "Device details approved successfully.";
		}
		if (deviceDetails.getApprovalStatus().equals(AuthDeviceConstant.REJECT)) {
			entity.setApprovalStatus(AuthDeviceConstant.REJECTED);
			entity.setIsActive(false);
			deviceDetailRepository.save(entity);
			return "Device details rejected successfully.";
		}

		auditUtil.auditRequest(
				String.format(AuthDeviceConstant.STATUS_UPDATE_FAILURE, RegDeviceDetail.class.getCanonicalName()),
				AuthDeviceConstant.AUDIT_SYSTEM,
				String.format(AuthDeviceConstant.FAILURE_DESC,
						DeviceDetailExceptionsConstant.DEVICE_STATUS_CODE.getErrorCode(),
						DeviceDetailExceptionsConstant.DEVICE_STATUS_CODE.getErrorMessage()),
				"AUT-008");
		throw new RequestException(DeviceDetailExceptionsConstant.DEVICE_STATUS_CODE.getErrorCode(), String
				.format(DeviceDetailExceptionsConstant.DEVICE_STATUS_CODE.getErrorMessage(), deviceDetails.getId()));
	}

	@PersistenceContext(unitName = "regDeviceEntityManagerFactory")
	private EntityManager entityManager;

	@Override
	public <E> PageResponseDto<DeviceDetailSearchResponseDto> searchDeviceDetails(Class<E> entity, DeviceSearchDto dto) {
		List<DeviceDetailSearchResponseDto> deviceDetails = new ArrayList<>();
		PageResponseDto<DeviceDetailSearchResponseDto> pageDto = new PageResponseDto<>();		
		Page<E> page = searchHelper.search(entityManager, entity, dto);
		if (page.getContent() != null && !page.getContent().isEmpty()) {
			deviceDetails = MapperUtils.mapAll(page.getContent(), DeviceDetailSearchResponseDto.class);
			pageDto = pageUtils.sortPage(deviceDetails, dto.getSort(), dto.getPagination(), page.getTotalElements());
		}
		return pageDto;
	}

	@Override
	public <E> PageResponseDto<RegistrationSubTypeDto> searchDeviceType(Class<E> entity, DeviceSearchDto dto) {
		List<RegistrationSubTypeDto> deviceSubTypes = new ArrayList<>();
		PageResponseDto<RegistrationSubTypeDto> pageDto = new PageResponseDto<>();
		Page<E> page = searchHelper.search(entityManager, entity, dto);
		if (page.getContent() != null && !page.getContent().isEmpty()) {
			deviceSubTypes = MapperUtils.mapAll(page.getContent(), RegistrationSubTypeDto.class);
			pageDto = pageUtils.sortPage(deviceSubTypes, dto.getSort(), dto.getPagination(), page.getTotalElements());
		}
		return pageDto;
	}

	@Override
	public FilterResponseCodeDto regDeviceFilterValues(DeviceFilterValueDto deviceFilterValueDto) {
		FilterResponseCodeDto filterResponseDto = new FilterResponseCodeDto();
		List<ColumnCodeValue> columnValueList = new ArrayList<>();
		if (filterColumnValidator.validate(FilterDto.class, deviceFilterValueDto.getFilters(), RegDeviceDetail.class)) {
			for (FilterDto filterDto : deviceFilterValueDto.getFilters()) {
				List<FilterData> filterValues = filterHelper.filterValuesWithCode(entityManager, RegDeviceDetail.class,
						filterDto, deviceFilterValueDto, "id");
				filterValues.forEach(filterValue -> {
					ColumnCodeValue columnValue = new ColumnCodeValue();
					columnValue.setFieldCode(filterValue.getFieldCode());
					columnValue.setFieldID(filterDto.getColumnName());
					columnValue.setFieldValue(filterValue.getFieldValue());
					columnValueList.add(columnValue);
				});
			}
			filterResponseDto.setFilters(columnValueList);
		}
		return filterResponseDto;
	}

	/**
	 * 
	 * @param deviceFilterValueDto
	 * @return
	 */
	@Override
	public FilterResponseCodeDto regDeviceTypeFilterValues(DeviceFilterValueDto deviceFilterValueDto) {
		FilterResponseCodeDto filterResponseDto = new FilterResponseCodeDto();
		List<ColumnCodeValue> columnValueList = new ArrayList<>();
		if (filterColumnValidator.validate(FilterDto.class, deviceFilterValueDto.getFilters(), RegRegistrationDeviceType.class)) {
			for (FilterDto filterDto : deviceFilterValueDto.getFilters()) {
				List<FilterData> filterValues = filterHelper.filterValuesWithCode(entityManager, RegRegistrationDeviceType.class,
						filterDto, deviceFilterValueDto, "code");
				filterValues.forEach(filterValue -> {
					ColumnCodeValue columnValue = new ColumnCodeValue();
					columnValue.setFieldCode(filterValue.getFieldCode());
					columnValue.setFieldID(filterDto.getColumnName());
					columnValue.setFieldValue(filterValue.getFieldValue());
					columnValueList.add(columnValue);
				});
			}
			filterResponseDto.setFilters(columnValueList);
		}
		return filterResponseDto;
	}
	
	/**
	 * 
	 * @param deviceFilterValueDto
	 * @return
	 */
	@Override
	public FilterResponseCodeDto regDeviceSubTypeFilterValues(DeviceFilterValueDto deviceFilterValueDto) {
		FilterResponseCodeDto filterResponseDto = new FilterResponseCodeDto();
		List<ColumnCodeValue> columnValueList = new ArrayList<>();
		if (filterColumnValidator.validate(FilterDto.class, deviceFilterValueDto.getFilters(), RegRegistrationDeviceSubType.class)) {
			for (FilterDto filterDto : deviceFilterValueDto.getFilters()) {
				List<FilterData> filterValues = filterHelper.filterValuesWithCode(entityManager, RegRegistrationDeviceSubType.class,
						filterDto, deviceFilterValueDto, "code");
				filterValues.forEach(filterValue -> {
					ColumnCodeValue columnValue = new ColumnCodeValue();
					columnValue.setFieldCode(filterValue.getFieldCode());
					columnValue.setFieldID(filterDto.getColumnName());
					columnValue.setFieldValue(filterValue.getFieldValue());
					columnValueList.add(columnValue);
				});
			}
			filterResponseDto.setFilters(columnValueList);
		}
		return filterResponseDto;
	}
}
