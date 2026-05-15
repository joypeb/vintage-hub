package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.product.application.ProductAvailabilityCheckResult;
import com.joypeb.vintagehub.product.application.ProductAvailabilityCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductAvailabilityController.class)
class AdminProductAvailabilityControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductAvailabilityCheckService availabilityCheckService;

	@Test
	void checkAvailabilityReturnsAcceptedResult() throws Exception {
		when(availabilityCheckService.checkProduct(1L))
			.thenReturn(new ProductAvailabilityCheckResult(1, 1, 0, 0, 0));

		mockMvc.perform(post("/api/admin/products/1/availability-check"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.checkedCount").value(1))
			.andExpect(jsonPath("$.data.availableCount").value(1))
			.andExpect(jsonPath("$.data.soldOutCount").value(0))
			.andExpect(jsonPath("$.data.unknownCount").value(0))
			.andExpect(jsonPath("$.data.failedCount").value(0))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void checkAvailabilityReturnsCommonErrorResponseForUnknownProduct() throws Exception {
		doThrow(new ResourceNotFoundException("Product not found: 99"))
			.when(availabilityCheckService)
			.checkProduct(99L);

		mockMvc.perform(post("/api/admin/products/99/availability-check"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("ERROR_002"))
			.andExpect(jsonPath("$.error.description").value("요청한 리소스를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.error.message").value("Product not found: 99"));
	}
}
