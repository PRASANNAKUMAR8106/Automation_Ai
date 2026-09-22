class UserModel {
  final String id;
  final String email;
  final String? firstName;
  final String? lastName;
  final String role;
  final bool emailVerified;
  final String? activeOrganizationId;
  final String? activeOrganizationName;
  final String? activeMembershipRole;

  const UserModel({
    required this.id,
    required this.email,
    this.firstName,
    this.lastName,
    required this.role,
    this.emailVerified = false,
    this.activeOrganizationId,
    this.activeOrganizationName,
    this.activeMembershipRole,
  });

  String get displayName {
    if (firstName != null && firstName!.isNotEmpty) {
      return lastName != null ? '$firstName $lastName' : firstName!;
    }
    return email.split('@').first;
  }

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] as String,
      email: json['email'] as String,
      firstName: json['firstName'] as String?,
      lastName: json['lastName'] as String?,
      role: json['role'] as String? ?? 'CUSTOMER',
      emailVerified: json['emailVerified'] as bool? ?? false,
      activeOrganizationId: json['activeOrganizationId'] as String?,
      activeOrganizationName: json['activeOrganizationName'] as String?,
      activeMembershipRole: json['activeMembershipRole'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'email': email,
      'firstName': firstName,
      'lastName': lastName,
      'role': role,
      'emailVerified': emailVerified,
      'activeOrganizationId': activeOrganizationId,
      'activeOrganizationName': activeOrganizationName,
      'activeMembershipRole': activeMembershipRole,
    };
  }
}
