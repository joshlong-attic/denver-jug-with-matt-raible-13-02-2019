import React, { Component } from 'react';
import { BrowserRouter as Router, Route } from 'react-router-dom';
import { Security, ImplicitCallback } from '@okta/okta-react';
import Home from './Home';

const config = {
  issuer: 'https://dev-737523.oktapreview.com/oauth2/default',
  redirect_uri: window.location.origin + '/implicit/callback',
  client_id: '0oaj2pqatz6wVayhk0h7'
};

export interface Auth {
  login(redirectUri: string): {};
  logout(redirectUri: string): {};
  isAuthenticated(): boolean;
  getAccessToken(): string;
}

class App extends React.Component {

  render() {
    return (
      <Router>
        <Security {...config}>
          <Route path="/" exact={true} component={Home}/>
          <Route path="/implicit/callback" component={ImplicitCallback}/>
        </Security>
      </Router>
    );
  }
}

export default App;
