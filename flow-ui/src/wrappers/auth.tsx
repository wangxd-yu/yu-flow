import React from 'react';
import { Navigate } from 'react-router-dom';

const AuthWrapper = ({ children }) => {
  const token = localStorage.getItem('flow_token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  console.log('token 存在，放行');
  return children;
};

export default AuthWrapper;
