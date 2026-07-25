import React from 'react';
// @ts-ignore
import { Navigate } from 'react-router-dom';
import { mayBeLoggedIn } from '@/services/auth';

const AuthWrapper = ({ children }: { children?: React.ReactNode }) => {
  if (!mayBeLoggedIn()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

export default AuthWrapper;
