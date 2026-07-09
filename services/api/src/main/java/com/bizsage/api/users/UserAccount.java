package com.bizsage.api.users;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bizsage.api.auth.Role;

@TableName("users")
public class UserAccount {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String username;
  @TableField("password_hash")
  private String password;
  private Role role;
  @TableField("phone_encrypted")
  private String phone;
  @TableField("identity_encrypted")
  private String identity;
  private String regionId;
  private String industryId;
  private String membershipLevel;
  private String consultationPreferences;

  public UserAccount() {
  }

  public UserAccount(Long id, String username, String password, Role role, String phone,
      String identity, String regionId, String industryId, String membershipLevel,
      String consultationPreferences) {
    this.id = id;
    this.username = username;
    this.password = password;
    this.role = role;
    this.phone = phone;
    this.identity = identity;
    this.regionId = regionId;
    this.industryId = industryId;
    this.membershipLevel = membershipLevel;
    this.consultationPreferences = consultationPreferences;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getIdentity() {
    return identity;
  }

  public void setIdentity(String identity) {
    this.identity = identity;
  }

  public String getRegionId() {
    return regionId;
  }

  public void setRegionId(String regionId) {
    this.regionId = regionId;
  }

  public String getIndustryId() {
    return industryId;
  }

  public void setIndustryId(String industryId) {
    this.industryId = industryId;
  }

  public String getMembershipLevel() {
    return membershipLevel;
  }

  public void setMembershipLevel(String membershipLevel) {
    this.membershipLevel = membershipLevel;
  }

  public String getConsultationPreferences() {
    return consultationPreferences;
  }

  public void setConsultationPreferences(String consultationPreferences) {
    this.consultationPreferences = consultationPreferences;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public Role role() {
    return role;
  }

  public String phone() {
    return phone;
  }

  public String identity() {
    return identity;
  }

  public String regionId() {
    return regionId;
  }

  public String industryId() {
    return industryId;
  }

  public String membershipLevel() {
    return membershipLevel;
  }

  public String consultationPreferences() {
    return consultationPreferences;
  }
}
