package vn.com.vndirect.exchangesimulator.service;

import vn.com.vndirect.exchangesimulator.model.SecurityStatus;

public interface SecurityService {

	SecurityStatus getSecurityBySymbol(String symbol);
}
