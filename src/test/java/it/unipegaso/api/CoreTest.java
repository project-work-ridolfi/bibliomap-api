package it.unipegaso.api;

import io.quarkus.elytron.security.common.BcryptUtil;

public class CoreTest {
	
	public static void main(String[] args) {
		
		String password = "Password110!";
		
		String hashPassword = BcryptUtil.bcryptHash(password);
		
		System.out.println("password: " + password +  " hash: " + hashPassword);
		
        boolean isPasswordValid = BcryptUtil.matches(password, hashPassword);
        
        System.out.println("is valid: " + isPasswordValid );

	}

}
